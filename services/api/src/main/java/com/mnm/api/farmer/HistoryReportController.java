package com.mnm.api.farmer;

import com.mnm.api.farmer.HistoryReportDtos.CopyTextResponse;
import com.mnm.api.farmer.HistoryReportDtos.HistoryReportResponse;
import com.mnm.api.farmer.HistoryReportDtos.ReportPatchRequest;
import com.mnm.api.farmer.HistoryReportDtos.SubmitRequest;
import com.mnm.api.farmer.HistoryReportDtos.SubmitResponse;
import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.FARMER + "/history-reports")
@RequiredArgsConstructor
class HistoryReportController {

    private final HistoryReportService historyReportService;

    @Operation(summary = "이력 신고서 초안 (FARM-06)", description = "빈칸·다음 질문·기한·직접 제출 가능 여부")
    @GetMapping("/{id}")
    BaseResponse<HistoryReportResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return BaseResponse.of(historyReportService.get(FarmerSupport.userId(jwt), id));
    }

    @Operation(summary = "신고서 빈칸 채우기 (FARM-07)", description = "보낸 칸만 바꾼다. null 은 지움. 초안일 때만")
    @PatchMapping("/{id}")
    BaseResponse<HistoryReportResponse> patch(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody ReportPatchRequest request) {
        return BaseResponse.of(historyReportService.patch(FarmerSupport.userId(jwt), id, request));
    }

    @Operation(summary = "직접 제출용 항목 텍스트 (FARM-08)")
    @GetMapping("/{id}/copy-text")
    BaseResponse<CopyTextResponse> copyText(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return BaseResponse.of(historyReportService.copyText(FarmerSupport.userId(jwt), id));
    }

    @Operation(summary = "신고 마침 · 축협에 부탁 (FARM-09)",
            description = "mode=self 는 빈칸이 없어야 한다. mode=coop 은 Idempotency-Key 헤더 필수 (같은 키는 CONFLICT-409)")
    @PostMapping("/{id}/submit")
    BaseResponse<SubmitResponse> submit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SubmitRequest request) {
        return BaseResponse.of(historyReportService.submit(FarmerSupport.userId(jwt), id, request.mode(), idempotencyKey));
    }
}
