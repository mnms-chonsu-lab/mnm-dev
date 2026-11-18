package com.mnm.api.farmer;

import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.global.response.PageResponse;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 농가 API 공통: 로그인한 농가의 농장 찾기, JSON 컬럼 읽고 쓰기, 커서 목록 만들기 */
@Component
@RequiredArgsConstructor
class FarmerSupport {

    static final int PAGE_SIZE = 20;

    private final FarmerFarmRepository farmRepository;
    private final ObjectMapper objectMapper;

    static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    /** 로그인한 농가의 농장 (계정은 어드민이 농장과 함께 만든다). 서비스의 트랜잭션 안에서 부른다 */
    FarmInfo farm(Long userId) {
        return farmRepository.findFirstByOwnerId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "농장 정보 없음"));
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
    static <E, T> PageResponse<T> page(List<E> rows, int size, Function<E, T> mapper, Function<E, Object> cursorOf) {
        boolean hasNext = rows.size() > size;
        List<E> items = hasNext ? rows.subList(0, size) : rows;
        String next = hasNext ? String.valueOf(cursorOf.apply(items.getLast())) : null;
        return new PageResponse<>(items.stream().map(mapper).toList(), hasNext, next);
    }

    /** 만 나이 (출생일 기준) */
    static Integer age(LocalDate birthDate) {
        return birthDate == null ? null : Period.between(birthDate, FarmerRules.today()).getYears();
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
}
