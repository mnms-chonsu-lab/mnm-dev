package com.mnm.api.global;

import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.global.response.BaseResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 공통 응답·예외 처리 확인용. 테스트 클래스패스에만 있다. */
@RestController
@RequestMapping(ApiPaths.FARMER + "/_test")
class CommonTestController {

    record Item(String farmName, Instant createdAt) {
    }

    record Form(@NotBlank(message = "귀표 번호를 넣어 주세요.") String earTag, @Min(1) int headCount) {
    }

    @GetMapping("/ok")
    BaseResponse<Item> ok() {
        return BaseResponse.of(new Item("한우농장", Instant.parse("2026-11-17T03:00:00Z")));
    }

    @PostMapping("/form")
    BaseResponse<Form> form(@Valid @RequestBody Form form) {
        return BaseResponse.of(form);
    }

    @GetMapping("/param")
    void param(@RequestParam @Min(1) int size) {
    }

    @GetMapping("/business")
    void business() {
        throw new BusinessException(ErrorCode.CONFLICT, "같은 전달 재요청");
    }

    @GetMapping("/integrity")
    void integrity() {
        throw new DataIntegrityViolationException("insert into users values ('secret')");
    }

    @GetMapping("/upload")
    void upload() {
        throw new MaxUploadSizeExceededException(10);
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException("select * from users where phone = '01012345678'");
    }
}
