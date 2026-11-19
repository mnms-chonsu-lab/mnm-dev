package com.mnm.api.receiver;

import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import com.mnm.api.receiver.JurisdictionDtos.JurisdictionResponse;
import com.mnm.api.receiver.JurisdictionDtos.NudgeResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.RECEIVER + "/jurisdiction/farms")
@RequiredArgsConstructor
class JurisdictionController {

    private final JurisdictionService jurisdictionService;

    @Operation(summary = "관할 농가 + 지표 (RCV-06)", description = "미응답 → 기한 → 소규모 순. 연락처는 공개한 농가만 전체 번호")
    @GetMapping
    BaseResponse<JurisdictionResponse> farms(@AuthenticationPrincipal Jwt jwt) {
        return BaseResponse.of(jurisdictionService.farms(ReceiverSupport.userId(jwt)));
    }

    @Operation(summary = "농가 알림 재발송 (RCV-07)", description = "48시간 넘게 안 읽은 알림을 다시 넣는다. 없으면 CONFLICT-409")
    @PostMapping("/{id}/nudge")
    @ResponseStatus(HttpStatus.CREATED)
    BaseResponse<NudgeResponse> nudge(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return BaseResponse.of(jurisdictionService.nudge(ReceiverSupport.userId(jwt), id));
    }
}
