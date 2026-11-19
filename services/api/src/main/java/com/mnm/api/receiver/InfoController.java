package com.mnm.api.receiver;

import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import com.mnm.api.receiver.InfoDtos.GuideDoc;
import com.mnm.api.receiver.InfoDtos.WeeklyStats;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.RECEIVER)
@RequiredArgsConstructor
class InfoController {

    private final InfoService infoService;

    @Operation(summary = "게시된 지침 요약 (RCV-08)", description = "어드민에서 게시한 요약만, 문서별로 묶어 개정일과 함께")
    @GetMapping("/guides")
    BaseResponse<List<GuideDoc>> guides(@AuthenticationPrincipal Jwt jwt) {
        return BaseResponse.of(infoService.guides(ReceiverSupport.userId(jwt)));
    }

    @Operation(summary = "주간 통계 (RCV-09)", description = "최근 7일(오늘 포함): 접수 · 처리 완료 · 사진 포함 · 날짜별 접수")
    @GetMapping("/stats/weekly")
    BaseResponse<WeeklyStats> weekly(@AuthenticationPrincipal Jwt jwt) {
        return BaseResponse.of(infoService.weekly(ReceiverSupport.userId(jwt)));
    }
}
