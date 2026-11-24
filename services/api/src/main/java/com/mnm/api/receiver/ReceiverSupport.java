package com.mnm.api.receiver;

import com.mnm.api.domain.audit.AuditLog;
import com.mnm.api.domain.delivery.Delivery;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.domain.user.User;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.global.response.PageResponse;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 접수자 API 공통: 로그인한 접수자와 소속 기관, 관할 확인, 감사 기록, JSON 컬럼 읽고 쓰기, 커서 목록 */
@Component
@RequiredArgsConstructor
class ReceiverSupport {

    static final int PAGE_SIZE = 20;
    /** 같은 접수자가 같은 대상을 이 시간 안에 다시 열면 열람 1회로 본다 (제안값, 실측으로 조정) */
    static final Duration VIEW_DEDUP = Duration.ofMinutes(1);

    private final ReceiverUserRepository userRepository;
    private final ReceiverAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    /** 로그인한 접수자. 소속 기관이 없으면 볼 수 있는 관할이 없으므로 AUTH-403. 서비스의 트랜잭션 안에서 부른다 */
    User receiver(Long userId) {
        User user = userRepository.findWithOrgById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_EXPIRED, "계정 없음"));
        if (user.getOrg() == null) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "소속 기관 없음");
        }
        return user;
    }

    /** 우리 기관이 받은(또는 함께 받은) 도착한 전달만 볼 수 있다. 다른 기관 것은 관할 밖 AUTH-403 */
    static void checkInbox(Delivery delivery, ReceiverOrg org) {
        if (!"delivered".equals(delivery.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "도착하지 않은 전달");
        }
        if (!isMine(delivery, org)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "관할 밖 전달");
        }
    }

    static boolean isMine(Delivery delivery, ReceiverOrg org) {
        return sameOrg(delivery.getOrg(), org) || sameOrg(delivery.getCcOrg(), org);
    }

    /** 관할 농가 = 우리 기관이 담당 축협이거나 농장 시군구가 관할 지역 */
    static boolean inJurisdiction(FarmInfo farm, ReceiverOrg org) {
        return sameOrg(farm.getOrg(), org)
                || farm.getRegionCode() != null && sigungu(org).contains(farm.getRegionCode().substring(0, 5));
    }

    /** 관할 법정동 코드(10자리)의 시군구(앞 5자리) */
    static Set<String> sigungu(ReceiverOrg org) {
        return Arrays.stream(org.getRegionCodes()).filter(Objects::nonNull).map(c -> c.substring(0, 5))
                .collect(Collectors.toSet());
    }

    /** 접수자의 열람·변경을 감사 기록에 남긴다 (COM-07). 서비스의 트랜잭션 안에서 부른다 */
    void audit(User user, String action, String targetType, Long targetId) {
        auditRepository.save(AuditLog.of(user, action, targetType, targetId, clientIp()));
    }

    /**
     * 열람 감사 기록. 화면이 같은 상세를 여러 번 불러도(옆 상세 + 팝업, 화면 이동) 1분 안의 열람은 1건만 남긴다.
     * 동시에 온 요청끼리는 호출하는 쪽이 대상 행을 잠가서 순서대로 확인하게 한다
     */
    void auditView(User user, String targetType, Long targetId) {
        if (!auditRepository.existsByUserIdAndActionAndTargetTypeAndTargetIdAndAtAfter(user.getId(), "view",
                targetType, targetId, Instant.now().minus(VIEW_DEDUP))) {
            audit(user, "view", targetType, targetId);
        }
    }

    Map<String, Object> readMap(String json) {
        return json == null ? new LinkedHashMap<>()
                : objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    List<String> readList(String json) {
        return json == null ? List.of() : objectMapper.readValue(json, new TypeReference<List<String>>() { });
    }

    String write(Object value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }

    /** size + 1 개를 읽어 다음 페이지가 있는지 판단한다 */
    static <E, T> PageResponse<T> page(List<E> rows, int size, Function<E, T> mapper, Function<E, Long> cursorOf) {
        boolean hasNext = rows.size() > size;
        List<E> items = hasNext ? rows.subList(0, size) : rows;
        String next = hasNext ? String.valueOf(cursorOf.apply(items.getLast())) : null;
        return new PageResponse<>(items.stream().map(mapper).toList(), hasNext, next);
    }

    static Long cursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Long.MAX_VALUE;
        }
        try {
            return Long.valueOf(cursor);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "cursor 형식 오류");
        }
    }

    private static boolean sameOrg(ReceiverOrg a, ReceiverOrg b) {
        return a != null && b != null && Objects.equals(a.getId(), b.getId());
    }

    /** nginx 뒤의 실제 접속 주소 (server.forward-headers-strategy). 주소 글자 그대로라 DNS 조회는 없다 */
    private static InetAddress clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        try {
            return InetAddress.getByName(attrs.getRequest().getRemoteAddr());
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
