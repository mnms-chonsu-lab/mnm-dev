package com.mnm.api.farmer;

import com.mnm.api.farmer.CowDtos.CowRegisterRequest;
import com.mnm.api.farmer.CowDtos.CowResponse;
import com.mnm.api.farmer.CowDtos.CowUpdateRequest;
import com.mnm.api.farmer.CowDtos.CowUpdateResponse;
import com.mnm.api.farmer.CowDtos.LookupResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.FARMER)
@RequiredArgsConstructor
class CowController {

    private final CowService cowService;

    @Operation(summary = "소 목록 (FARM-02)", description = "목록에 있는 소. 최근 일지가 있는 소 먼저 (페이지 없음, has_next=false)")
    @GetMapping("/farms/me/cows")
    BaseResponse<PageResponse<CowResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        return BaseResponse.of(cowService.list(FarmerSupport.userId(jwt)));
    }

    @Operation(summary = "귀표 → 이력제 조회 (FARM-22)",
            description = "등록 전 확인용. 못 찾음 CATTLE-NOTFOUND(404) · 조회 실패 TRACE-API-FAIL(502) · 다른 농장 CATTLE-OTHER-FARM(409)")
    @GetMapping("/cows/lookup")
    BaseResponse<LookupResponse> lookup(@AuthenticationPrincipal Jwt jwt, @RequestParam("tag_no") String tagNo) {
        return BaseResponse.of(cowService.lookup(FarmerSupport.userId(jwt), tagNo));
    }

    @Operation(summary = "소 등록 (FARM-23)",
            description = "이력제 값 또는 직접 입력(sex·birth_date). 이미 등록된 귀표는 CONFLICT-409, 다른 농장 소는 confirm_other_farm=true 필요")
    @PostMapping("/farms/me/cows")
    @ResponseStatus(HttpStatus.CREATED)
    BaseResponse<CowResponse> register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CowRegisterRequest request) {
        return BaseResponse.of(cowService.register(FarmerSupport.userId(jwt), request));
    }

    @Operation(summary = "축사 수정 · 목록에서 빼기 · 이력제 다시 불러오기 (FARM-24)",
            description = "create_report 면 폐사·양도 신고서 초안을 함께 만든다. refresh_trace 면 이력제 값으로 다시 채운다 "
                    + "(못 찾음 CATTLE-NOTFOUND · 조회 실패 TRACE-API-FAIL · 다른 농장 CATTLE-OTHER-FARM)")
    @PatchMapping("/cows/{id}")
    BaseResponse<CowUpdateResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody CowUpdateRequest request) {
        return BaseResponse.of(cowService.update(FarmerSupport.userId(jwt), id, request));
    }
}
