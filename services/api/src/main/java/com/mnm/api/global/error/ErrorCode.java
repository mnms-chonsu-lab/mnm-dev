package com.mnm.api.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * 클라이언트에 돌려주는 오류 코드. 코드·문구는 기능명세서 "오류·예외 처리" 탭 기준.
 * AI 오류 코드(STT-FAIL 등)는 서버 안에서 대체 경로로 처리하므로 여기에 두지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    AUTH_EXPIRED("AUTH-401", HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요. 쓰던 기록은 그대로 있어요."),
    AUTH_FORBIDDEN("AUTH-403", HttpStatus.FORBIDDEN, "볼 수 없는 화면이에요."),
    AUTH_FAIL("AUTH-FAIL", HttpStatus.UNAUTHORIZED, "농장식별번호나 휴대폰 번호를 다시 확인해 주세요."),
    /** 남은 시간이 들어간 문구는 인증 구현에서 BusinessException 의 message 로 넘긴다 */
    AUTH_LOCK("AUTH-LOCK", HttpStatus.LOCKED, "5번 틀려서 10분 동안 로그인할 수 없어요. 농장식별번호는 담당 축협에 물어보세요."),
    /** 칸별 문구는 ErrorResponse.errors 에 담는다 */
    INVALID_INPUT("VALID-422", HttpStatus.UNPROCESSABLE_CONTENT, "입력한 내용을 다시 확인해 주세요."),
    NOT_FOUND("NF-404", HttpStatus.NOT_FOUND, "찾는 기록이 없어요. 홈에서 다시 찾아 주세요."),
    METHOD_NOT_ALLOWED("METHOD-405", HttpStatus.METHOD_NOT_ALLOWED, "요청을 처리할 수 없어요."),
    CONFLICT("CONFLICT-409", HttpStatus.CONFLICT, "이미 보냈어요."),
    UPLOAD_FAIL("UPLOAD-FAIL", HttpStatus.CONTENT_TOO_LARGE, "사진을 보내지 못했어요. 다시 올려 주세요."),
    SERVER_ERROR("SRV-5XX", HttpStatus.INTERNAL_SERVER_ERROR, "잠시 문제가 생겼어요. 입력하신 내용은 그대로 있어요. 다시 시도해 주세요.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    /** 스프링이 HTTP 상태로만 알려주는 오류(없는 경로, 잘못된 메서드 등)를 코드로 바꾼다 */
    public static ErrorCode fromStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> AUTH_EXPIRED;
            case 403 -> AUTH_FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONFLICT;
            case 413 -> UPLOAD_FAIL;
            default -> status.is4xxClientError() ? INVALID_INPUT : SERVER_ERROR;
        };
    }
}
