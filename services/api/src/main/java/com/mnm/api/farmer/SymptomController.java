package com.mnm.api.farmer;

import com.mnm.api.farmer.SymptomDtos.ActionsResponse;
import com.mnm.api.farmer.SymptomDtos.QuestionResponse;
import com.mnm.api.farmer.SymptomDtos.SymptomReportRequest;
import com.mnm.api.farmer.SymptomDtos.SymptomReportResponse;
import com.mnm.api.farmer.SymptomDtos.SymptomSummaryResponse;
import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import com.mnm.api.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
class SymptomController {

    private final SymptomService symptomService;

    @Operation(summary = "공식 증상 질문 (FARM-10)", description = "게시된 질문만, 순서대로 (침 → 물집 → 사료 → 혹)")
    @GetMapping("/guide/questions")
    BaseResponse<PageResponse<QuestionResponse>> questions() {
        return BaseResponse.of(new PageResponse<>(symptomService.questions(), false, null));
    }

    @Operation(summary = "소 상태 기록 저장 (FARM-11)", description = "공식 증상 일치 수(판정 아님) · 찍을 부위 · 지침 분기(disease) 반환")
    @PostMapping("/symptom-reports")
    @ResponseStatus(HttpStatus.CREATED)
    BaseResponse<SymptomReportResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SymptomReportRequest request) {
        return BaseResponse.of(symptomService.create(FarmerSupport.userId(jwt), request));
    }

    @Operation(summary = "오늘 기록 요약 (FARM-14)", description = "같은 소 최근 7일 일지 · 최근 접종 · 사진 결과 · 인터넷 신고서 6개 항목")
    @GetMapping("/symptom-reports/{id}/summary")
    BaseResponse<SymptomSummaryResponse> summary(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return BaseResponse.of(symptomService.summary(FarmerSupport.userId(jwt), id));
    }

    @Operation(summary = "질병별 지금 할 일 (FARM-16)", description = "disease = FARM-11 응답의 disease (FMD · LSD). 게시된 원문 최대 3개")
    @GetMapping("/guide/actions")
    BaseResponse<ActionsResponse> actions(
            @RequestParam @Pattern(regexp = "(?i)FMD|LSD", message = "안내를 찾을 수 없어요.") String disease) {
        return BaseResponse.of(symptomService.actions(disease));
    }
}
