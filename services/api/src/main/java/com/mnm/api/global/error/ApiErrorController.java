package com.mnm.api.global.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 컨트롤러 밖(필터·인증 단계 등)에서 난 오류도 ErrorResponse 형식으로 돌려준다.
 * 이 빈이 있으면 스프링 기본 BasicErrorController 는 등록되지 않는다.
 */
@Slf4j
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("${server.error.path:/error}")
    ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        int status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer s ? s : 500;
        ErrorCode errorCode = ErrorCode.fromStatus(HttpStatusCode.valueOf(status));
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("[{}] {} {}", errorCode.getCode(), request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI),
                    status, (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
        }
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, errorCode.getMessage(), null, List.of()));
    }
}
