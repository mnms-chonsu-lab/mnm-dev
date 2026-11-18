package com.mnm.api.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * 클라이언트에 돌려주는 오류 코드. 코드·문구는 기능명세서 "오류·예외 처리" 탭 기준.
 * message 는 명세의 "농가 문구"다. 접수자 웹·어드민은 code 를 보고 화면 문구를 따로 정한다.
 * 문구 속 {중괄호}는 변수다. 값을 채운 문구는 BusinessException(code, message, detail)로 넘긴다.
 * AI 오류 코드(STT-FAIL 등)는 서버 안에서 대체 경로로 처리하므로 여기에 두지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    AUTH_EXPIRED("AUTH-401", HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요. 쓰던 기록은 그대로 있어요."),
    AUTH_FORBIDDEN("AUTH-403", HttpStatus.FORBIDDEN, "볼 수 없는 화면이에요."),
    /** 정지된 계정 로그인. AUTH-403 과 화면 처리가 달라 따로 둔다 (로그인 화면 입력 칸 아래 안내) */
    AUTH_SUSPENDED("AUTH-SUSPENDED", HttpStatus.FORBIDDEN, "로그인할 수 없는 계정이에요. 담당 기관에 물어보세요."),
    AUTH_FAIL("AUTH-FAIL", HttpStatus.UNAUTHORIZED, "농장식별번호나 휴대폰 번호를 다시 확인해 주세요."),
    /** 남은 시간이 들어간 문구는 인증 구현에서 BusinessException 의 message 로 넘긴다 */
    AUTH_LOCK("AUTH-LOCK", HttpStatus.LOCKED, "5번 틀려서 10분 동안 로그인할 수 없어요. 농장식별번호는 담당 축협에 물어보세요."),
    /** 칸별 문구는 ErrorResponse.errors 에 담는다 */
    INVALID_INPUT("VALID-422", HttpStatus.UNPROCESSABLE_CONTENT, "입력한 내용을 다시 확인해 주세요."),
    NOT_FOUND("NF-404", HttpStatus.NOT_FOUND, "찾는 기록이 없어요. 홈에서 다시 찾아 주세요."),
    METHOD_NOT_ALLOWED("METHOD-405", HttpStatus.METHOD_NOT_ALLOWED, "요청을 처리할 수 없어요. 앱을 새로 고친 뒤 다시 시도해 주세요."),
    /** 같은 전달 재요청(Idempotency-Key 중복), 다른 접수자가 먼저 상태 변경 */
    CONFLICT("CONFLICT-409", HttpStatus.CONFLICT, "이미 보냈어요."),
    UPLOAD_FAIL("UPLOAD-FAIL", HttpStatus.CONTENT_TOO_LARGE, "사진을 보내지 못했어요. 다시 올려 주세요."),

    /** 귀표 번호 이력 조회 결과 0건 → 직접 입력(성별·출생일) 안내 */
    CATTLE_NOT_FOUND("CATTLE-NOTFOUND", HttpStatus.NOT_FOUND, "이 번호로 등록된 소를 찾지 못했어요. 번호를 다시 확인해 주세요."),
    /** 조회된 농장번호 ≠ 로그인한 농장. 농가가 확인하면 다시 요청해 등록을 허용한다 */
    CATTLE_OTHER_FARM("CATTLE-OTHER-FARM", HttpStatus.CONFLICT, "다른 농장에 등록된 소로 나와요. 우리 농장 소가 맞나요?"),
    /** 축산물이력제 API 오류·타임아웃·호출 한도 초과 → 직접 입력으로 등록, 나중에 재조회 */
    TRACE_API_FAIL("TRACE-API-FAIL", HttpStatus.BAD_GATEWAY, "지금은 소 정보를 불러올 수 없어요. 성별과 출생일만 넣어 주세요."),
    /** 수신함 전달 저장 실패 → 완료로 넘기지 않고 [다시 시도] [전화로 신고] [나중에 보내기] */
    DELIVER_FAIL("DELIVER-FAIL", HttpStatus.INTERNAL_SERVER_ERROR, "아직 보내지 못했어요. 다시 보내거나 전화로 신고해 주세요."),

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
