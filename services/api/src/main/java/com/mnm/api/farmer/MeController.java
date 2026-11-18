package com.mnm.api.farmer;

import com.mnm.api.farmer.MeDtos.AlertResponse;
import com.mnm.api.farmer.MeDtos.FarmResponse;
import com.mnm.api.farmer.MeDtos.FarmUpdateRequest;
import com.mnm.api.farmer.MeDtos.FeedbackRequest;
import com.mnm.api.farmer.MeDtos.SettingsRequest;
import com.mnm.api.farmer.MeDtos.SettingsResponse;
import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import com.mnm.api.global.response.PageResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.FARMER)
@RequiredArgsConstructor
class MeController {

    private final MeService meService;

    @Operation(summary = "알림 목록 (FARM-18)", description = "최근순 20건. 연 알림은 읽음으로 표시한다")
    @GetMapping("/alerts")
    BaseResponse<PageResponse<AlertResponse>> alerts(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor) {
        return BaseResponse.of(meService.alerts(FarmerSupport.userId(jwt), cursor));
    }

    @Operation(summary = "알림 확인함 · 문제 없음 (FARM-19)")
    @PostMapping("/alerts/{id}/feedback")
    BaseResponse<AlertResponse> feedback(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody FeedbackRequest request) {
        return BaseResponse.of(meService.feedback(FarmerSupport.userId(jwt), id, request.feedback()));
    }

    @Operation(summary = "화면 설정 (FARM-20)")
    @GetMapping("/me/settings")
    BaseResponse<SettingsResponse> settings(@AuthenticationPrincipal Jwt jwt) {
        return BaseResponse.of(meService.settings(FarmerSupport.userId(jwt)));
    }

    @Operation(summary = "화면 설정 바꾸기 (FARM-20)", description = "보낸 값만 바꾼다")
    @PatchMapping("/me/settings")
    BaseResponse<SettingsResponse> updateSettings(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SettingsRequest request) {
        return BaseResponse.of(meService.updateSettings(FarmerSupport.userId(jwt), request));
    }

    @Operation(summary = "농장 정보 (FARM-20)", description = "내 소 수 · 보낸 기록 수 포함")
    @GetMapping("/me/farm")
    BaseResponse<FarmResponse> farm(@AuthenticationPrincipal Jwt jwt) {
        return BaseResponse.of(meService.farm(FarmerSupport.userId(jwt)));
    }

    @Operation(summary = "연락처 바꾸기 (FARM-20)", description = "바꾼 번호로 다음에 로그인한다. 다른 농가와 같은 번호면 CONFLICT-409")
    @PatchMapping("/me/farm")
    BaseResponse<FarmResponse> updateFarm(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FarmUpdateRequest request) {
        return BaseResponse.of(meService.changePhone(FarmerSupport.userId(jwt), request.phone()));
    }
}
