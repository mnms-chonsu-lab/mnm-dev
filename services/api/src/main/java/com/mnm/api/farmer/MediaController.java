package com.mnm.api.farmer;

import com.mnm.api.farmer.MediaDtos.BehaviorResponse;
import com.mnm.api.farmer.MediaDtos.PartCheckResponse;
import com.mnm.api.farmer.MediaDtos.UploadUrlRequest;
import com.mnm.api.farmer.MediaDtos.UploadUrlResponse;
import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.FARMER)
@RequiredArgsConstructor
class MediaController {

    private final MediaService mediaService;

    @Operation(summary = "사진·영상 업로드 URL (FARM-12)",
            description = "field_media 를 만들고 저장소 POST 양식을 준다. 앱은 upload_url 로 fields + file 을 multipart POST (사진 10MB · 영상 100MB 이하, 만료 10분)")
    @PostMapping("/media/upload-url")
    @ResponseStatus(HttpStatus.CREATED)
    BaseResponse<UploadUrlResponse> uploadUrl(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadUrlRequest request) {
        return BaseResponse.of(mediaService.uploadUrl(FarmerSupport.userId(jwt), request));
    }

    @Operation(summary = "부위 확인 (FARM-13)",
            description = "업로드 후 부른다. 3초 안에 끝나면 done, 늦으면 pending (결과는 FARM-14), 실패하면 failed (사진은 저장됨)")
    @PostMapping("/media/{id}/part-check")
    BaseResponse<PartCheckResponse> partCheck(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return BaseResponse.of(mediaService.partCheck(FarmerSupport.userId(jwt), id));
    }

    @Operation(summary = "행동 영상 분류 (FARM-21)",
            description = "업로드 후 부른다. 서기·눕기·먹기 비율(%, 참고). 늦으면 pending, 실패하면 failed (영상은 저장됨)")
    @PostMapping("/media/{id}/behavior")
    BaseResponse<BehaviorResponse> behavior(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return BaseResponse.of(mediaService.behavior(FarmerSupport.userId(jwt), id));
    }
}
