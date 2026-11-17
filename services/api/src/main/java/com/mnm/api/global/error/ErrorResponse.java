package com.mnm.api.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mnm.api.global.web.RequestIdFilter;
import java.util.List;

/**
 * 모든 오류 응답의 형식: { code, message, detail, request_id }.
 * 농가 화면은 message 만, 접수자·어드민은 request_id 를 "오류 번호"로 표시한다.
 */
public record ErrorResponse(
        String code,
        String message,
        String detail,
        String requestId,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldErrorDetail> errors) {

    /** 입력 오류일 때 칸별 문구. field 는 JSON 필드 이름(snake_case) */
    public record FieldErrorDetail(String field, String message) {
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String detail, List<FieldErrorDetail> errors) {
        return new ErrorResponse(errorCode.getCode(), message, detail, RequestIdFilter.current(), errors);
    }
}
