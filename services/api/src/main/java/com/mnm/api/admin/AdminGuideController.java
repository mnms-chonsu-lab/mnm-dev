package com.mnm.api.admin;

import com.mnm.api.admin.AdminGuideDtos.CreateGuideRequest;
import com.mnm.api.admin.AdminGuideDtos.GuideResponse;
import com.mnm.api.admin.AdminGuideDtos.UpdateGuideRequest;
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
@RequestMapping(ApiPaths.ADMIN + "/guides")
@RequiredArgsConstructor
class AdminGuideController {

    private final AdminGuideService guideService;

    @Operation(summary = "안내 문구 목록 (ADM-07, A-S05)", description = "kind · status · disease 로 거른다. 분류별 화면 순서대로")
    @GetMapping
    BaseResponse<PageResponse<GuideResponse>> list(@RequestParam(required = false) String kind,
            @RequestParam(required = false) String status, @RequestParam(required = false) String disease) {
        return BaseResponse.of(guideService.list(kind, status, disease));
    }

    @Operation(summary = "안내 문구 등록 (ADM-07, A-S05)", description = "초안으로 만든다")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BaseResponse<GuideResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateGuideRequest request) {
        return BaseResponse.of(guideService.create(AdminSupport.userId(jwt), request));
    }

    @Operation(summary = "안내 문구 수정 · 검수 · 게시 (ADM-08, A-S05)",
            description = "게시된 문구의 content 를 바꾸면 새 버전(초안)을 만들어 돌려준다. 초안 → 게시는 안 되고 검수 중을 거친다. 새 버전을 게시하면 이전 게시 버전은 초안으로 내려 보관한다")
    @PatchMapping("/{id}")
    BaseResponse<GuideResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody UpdateGuideRequest request) {
        return BaseResponse.of(guideService.update(AdminSupport.userId(jwt), id, request));
    }
}
