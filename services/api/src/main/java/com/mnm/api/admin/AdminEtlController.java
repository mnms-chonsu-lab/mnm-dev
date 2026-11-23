package com.mnm.api.admin;

import com.mnm.api.admin.AdminEtlService.JobRow;
import com.mnm.api.admin.AdminEtlService.RunRow;
import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import com.mnm.api.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.ADMIN + "/etl/jobs")
@RequiredArgsConstructor
class AdminEtlController {

    private final AdminEtlService etlService;

    @Operation(summary = "공공데이터 수집 현황 (ADM-09, A-S06)",
            description = "작업 3개(outbreak · farm_scale · disinfection_facility)의 주기 · 마지막 실행 · 마지막 성공 · 실행 중 여부")
    @GetMapping
    BaseResponse<List<JobRow>> jobs() {
        return BaseResponse.of(etlService.jobs());
    }

    @Operation(summary = "수집 실행 기록 (ADM-09, A-S06)", description = "최근순 20개씩, next_cursor 를 cursor 로 넘긴다")
    @GetMapping("/{job}/runs")
    BaseResponse<PageResponse<RunRow>> runs(@PathVariable String job, @RequestParam(required = false) String cursor) {
        return BaseResponse.of(etlService.runs(job, cursor));
    }

    @Operation(summary = "지금 수집 (ADM-09, A-S06)",
            description = "requested 기록만 남기고, 수집 서버 예약 작업(5분마다)이 실행한다. 요청됐거나 실행 중이면 CONFLICT-409")
    @PostMapping("/{job}/run")
    @ResponseStatus(HttpStatus.CREATED)
    BaseResponse<RunRow> run(@AuthenticationPrincipal Jwt jwt, @PathVariable String job) {
        return BaseResponse.of(etlService.request(AdminSupport.userId(jwt), job));
    }
}
