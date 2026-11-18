package com.mnm.api.admin;

import com.mnm.api.admin.AdminOrgDtos.OrgRequest;
import com.mnm.api.admin.AdminOrgDtos.OrgResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.ADMIN + "/orgs")
@RequiredArgsConstructor
class AdminOrgController {

    private final AdminOrgService orgService;

    @Operation(summary = "접수 기관 목록 (ADM-05, A-S09)", description = "수신함별 접수자 수 포함 (0명이면 화면에 \"받는 사람 없음\")")
    @GetMapping
    BaseResponse<PageResponse<OrgResponse>> list() {
        return BaseResponse.of(orgService.list());
    }

    @Operation(summary = "기관 추가 (ADM-05, A-S09)", description = "새 수신함은 inbox_id 없이 보내면 서버가 만든다")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BaseResponse<OrgResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OrgRequest request) {
        return BaseResponse.of(orgService.create(AdminSupport.userId(jwt), request));
    }

    @Operation(summary = "기관 수정 (ADM-05, A-S09)",
            description = "편집 패널의 전체 값을 보낸다. 접수자가 있는 수신함을 빼면 CONFLICT-409")
    @PatchMapping("/{id}")
    BaseResponse<OrgResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody OrgRequest request) {
        return BaseResponse.of(orgService.update(AdminSupport.userId(jwt), id, request));
    }
}
