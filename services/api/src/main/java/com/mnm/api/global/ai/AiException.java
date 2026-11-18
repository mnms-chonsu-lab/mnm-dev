package com.mnm.api.global.ai;

import lombok.Getter;

/**
 * AI 호출 실패 (STT-FAIL · LLM-FAIL 등). 클라이언트 오류 코드로 내보내지 않고,
 * 서비스가 받아서 명세의 대체 경로로 진행한다 (CLAUDE.md "외부 API·AI 호출이 실패해도 사용자 흐름을 멈추지 않습니다").
 */
@Getter
public class AiException extends RuntimeException {

    /** AI 서비스 오류 코드 (STT-FAIL, LLM-FAIL, PART-FAIL, BEHAVIOR-FAIL, RAG-NONE) */
    private final String code;

    public AiException(String code, String detail) {
        super(code + ": " + detail);
        this.code = code;
    }
}
