package com.mnm.api.farmer;

import com.mnm.api.farmer.HomeDtos.FacilityResponse;
import com.mnm.api.farmer.HomeDtos.HomeResponse;
import com.mnm.api.farmer.HomeDtos.RegionOutbreak;
import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.FARMER)
@RequiredArgsConstructor
class HomeController {

    private final HomeService homeService;

    @Operation(summary = "홈 요약 (FARM-05)", description = "할 일(기한 임박순), 미확인 알림 수, 우리 지역 최근 30일 발생")
    @GetMapping("/home")
    BaseResponse<HomeResponse> home(@AuthenticationPrincipal Jwt jwt) {
        return BaseResponse.of(homeService.home(FarmerSupport.userId(jwt)));
    }

    @Operation(summary = "우리 지역 발생 현황 (FARM-17)", description = "농장 시군구 기준 최근 30일 구제역·럼피스킨 건수 + 수집 기준 시각")
    @GetMapping("/region/outbreaks")
    BaseResponse<RegionOutbreak> outbreaks(@AuthenticationPrincipal Jwt jwt) {
        return BaseResponse.of(homeService.region(FarmerSupport.userId(jwt)));
    }

    @Operation(summary = "가장 가까운 거점소독시설 (FARM-17)", description = "농장 위경도가 없으면 data = null")
    @GetMapping("/facilities/nearest")
    BaseResponse<FacilityResponse> nearestFacility(@AuthenticationPrincipal Jwt jwt) {
        return BaseResponse.of(homeService.nearestFacility(FarmerSupport.userId(jwt)));
    }
}
