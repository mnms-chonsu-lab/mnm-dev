package com.mnm.api.admin;

import com.mnm.api.admin.AdminDashboardDtos.HealthResponse;
import com.mnm.api.admin.AdminDashboardDtos.TodayMetrics;
import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.ADMIN)
@RequiredArgsConstructor
class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @Operation(summary = "서비스 상태 (ADM-01, A-S01)", description = "api · db · ai(최근 1시간 호출) · etl(작업별 마지막 실행)")
    @GetMapping("/health")
    BaseResponse<HealthResponse> health() {
        return BaseResponse.of(dashboardService.health());
    }

    @Operation(summary = "오늘 수치 (ADM-01, A-S01)", description = "한국 시간 0시부터. 기록 수 · 전달 수 · 미처리 접수 · AI 오류율")
    @GetMapping("/metrics/today")
    BaseResponse<TodayMetrics> today() {
        return BaseResponse.of(dashboardService.today());
    }
}
