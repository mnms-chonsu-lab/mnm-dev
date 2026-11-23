package com.mnm.api.admin;

import com.mnm.api.admin.AdminAiService.AiMetrics;
import com.mnm.api.admin.AdminAiService.InferenceLogRow;
import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import com.mnm.api.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.ADMIN)
@RequiredArgsConstructor
class AdminAiController {

    private final AdminAiService aiService;

    @Operation(summary = "AI 지표 (ADM-11, A-S07)",
            description = "최근 24시간 기능별 호출 수 · 실패 수(503·504) · 오류율 · 평균 지연, 평소와 다름 \"문제 없음\" 비율")
    @GetMapping("/ai-metrics")
    BaseResponse<AiMetrics> metrics() {
        return BaseResponse.of(aiService.metrics());
    }

    @Operation(summary = "AI 호출 로그 (ADM-11, A-S07)",
            description = "feature(stt · route · part_check · behavior · guide · summary) · 기간 [from, to) 로 거른다. "
                    + "최근순 20개씩, next_cursor 를 cursor 로 넘긴다")
    @GetMapping("/inference-logs")
    BaseResponse<PageResponse<InferenceLogRow>> logs(@RequestParam(required = false) String feature,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String cursor) {
        return BaseResponse.of(aiService.logs(feature, from, to, cursor));
    }
}
