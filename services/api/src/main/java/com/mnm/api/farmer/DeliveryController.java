package com.mnm.api.farmer;

import com.mnm.api.farmer.DeliveryDtos.DeliveryRequest;
import com.mnm.api.farmer.DeliveryDtos.DeliveryResponse;
import com.mnm.api.farmer.DeliveryDtos.SentRecordResponse;
import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import com.mnm.api.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.FARMER + "/deliveries")
@RequiredArgsConstructor
class DeliveryController {

    private final DeliveryService deliveryService;

    @Operation(summary = "승인 → 전달 (FARM-15)",
            description = "Idempotency-Key 헤더 필수 (같은 키는 CONFLICT-409). 받을 기관이 없으면 DELIVER-FAIL")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BaseResponse<DeliveryResponse> deliver(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody DeliveryRequest request) {
        return BaseResponse.of(deliveryService.deliver(FarmerSupport.userId(jwt), idempotencyKey, request));
    }

    @Operation(summary = "내가 보낸 기록 · 접수 상태 (FARM-27)", description = "최근순 20건. next_cursor 를 cursor 로 넘긴다")
    @GetMapping("/mine")
    BaseResponse<PageResponse<SentRecordResponse>> mine(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor) {
        return BaseResponse.of(deliveryService.mine(FarmerSupport.userId(jwt), cursor));
    }
}
