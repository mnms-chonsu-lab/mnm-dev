package com.mnm.api.global.error;

import lombok.Getter;

/** 서비스 코드는 이 예외만 던진다. GlobalExceptionHandler 가 ErrorResponse 로 바꾼다. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    /** 사용자에게 보이는 문구 (기본값: ErrorCode 문구) */
    private final String userMessage;
    /** 개발용 설명. 개인정보·SQL·스택을 넣지 않는다 */
    private final String detail;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        this(errorCode, errorCode.getMessage(), detail);
    }

    public BusinessException(ErrorCode errorCode, String userMessage, String detail) {
        super(errorCode.getCode() + (detail == null ? "" : ": " + detail));
        this.errorCode = errorCode;
        this.userMessage = userMessage;
        this.detail = detail;
    }
}
