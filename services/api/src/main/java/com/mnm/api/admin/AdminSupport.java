package com.mnm.api.admin;

import com.mnm.api.domain.audit.AuditLog;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.global.response.PageResponse;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 어드민 API 공통: 로그인한 운영자, 감사 기록 남기기, 커서 목록, JSON 컬럼 읽고 쓰기 */
@Component
@RequiredArgsConstructor
class AdminSupport {

    static final int PAGE_SIZE = 20;
    /** 오늘 수치·최근 30일 같은 날짜 경계는 한국 시간 기준 */
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AdminUserRepository userRepository;
    private final AdminAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    /** 운영자의 계정·문구 변경을 감사 기록에 남긴다 (COM-07). 서비스의 트랜잭션 안에서 부른다 */
    void audit(Long adminId, String action, String targetType, Long targetId) {
        auditRepository.save(AuditLog.of(userRepository.getReferenceById(adminId), action, targetType, targetId,
                clientIp()));
    }

    <T> List<T> readList(String json, TypeReference<List<T>> type) {
        return json == null ? List.of() : objectMapper.readValue(json, type);
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

    /** 010-1234-0937 → 010-****-0937 (A-S02 연락처 마스킹) */
    static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = digits(phone);
        if (digits.length() < 8) {
            return "****";
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }

    static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
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
