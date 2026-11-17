package com.mnm.api.global.error;

import com.mnm.api.global.error.ErrorResponse.FieldErrorDetail;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 컨트롤러에서 난 모든 예외를 ErrorResponse 형식으로 바꾼다.
 * 스프링 기본 예외(없는 경로, 잘못된 JSON, 메서드·미디어 타입, 업로드 용량 등)는 ResponseEntityExceptionHandler 가 받아
 * handleExceptionInternal 로 모인다. 컨트롤러 밖(필터)에서 난 오류는 ApiErrorController 가 처리한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Object> handleBusiness(BusinessException e) {
        return respond(e.getErrorCode(), e.getUserMessage(), e.getDetail(), List.of(), null, e);
    }

    /** 서비스 계층의 @Validated 검증 실패 */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException e) {
        List<FieldErrorDetail> errors = e.getConstraintViolations().stream()
                .map(v -> new FieldErrorDetail(toJsonName(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return respond(ErrorCode.INVALID_INPUT, null, null, errors, null, e);
    }

    /** 중복 저장·동시 수정. 예외 메시지에 SQL 이 들어 있어 detail 에 넣지 않는다 */
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ResponseEntity<Object> handleConflict(Exception e) {
        return respond(ErrorCode.CONFLICT, null, null, List.of(), null, e);
    }

    /** 처리하지 못한 예외. 원인은 서버 로그에만 남기고 응답에는 넣지 않는다 */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnknown(Exception e) {
        return respond(ErrorCode.SERVER_ERROR, null, null, List.of(), null, e);
    }

    /** @Valid @RequestBody 검증 실패 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldErrorDetail(toJsonName(f.getField()), f.getDefaultMessage()))
                .toList();
        return respond(ErrorCode.INVALID_INPUT, null, null, errors, headers, ex);
    }

    /** @RequestParam · @PathVariable 등 메서드 파라미터 검증 실패 */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDetail> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldErrorDetail(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return respond(ErrorCode.INVALID_INPUT, null, null, errors, headers, ex);
    }

    /** 나머지 스프링 기본 예외. detail 은 스프링이 노출용으로 만든 문구(ProblemDetail.detail)만 쓴다 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode errorCode = ErrorCode.fromStatus(statusCode);
        String detail = errorCode == ErrorCode.SERVER_ERROR || !(ex instanceof org.springframework.web.ErrorResponse er)
                ? null
                : er.getBody().getDetail();
        return respond(errorCode, null, detail, List.of(), headers, ex);
    }

    private ResponseEntity<Object> respond(ErrorCode errorCode, String message, String detail,
            List<FieldErrorDetail> errors, HttpHeaders headers, Exception ex) {
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("[{}] {}", errorCode.getCode(), ex.toString(), ex);
        } else {
            log.warn("[{}] {}", errorCode.getCode(), ex.toString());
        }
        ErrorResponse body = ErrorResponse.of(errorCode, message == null ? errorCode.getMessage() : message, detail, errors);
        return ResponseEntity.status(errorCode.getStatus()).headers(headers).body(body);
    }

    /** 자바 필드 이름(earTag, items[0].earTag)을 JSON 필드 이름(ear_tag)으로 바꾼다 */
    private static String toJsonName(String path) {
        // Jackson SNAKE_CASE 와 같은 규칙 (URLValue → url_value). Jackson 3 의 translate 는 외부에서 못 부른다
        return path.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
