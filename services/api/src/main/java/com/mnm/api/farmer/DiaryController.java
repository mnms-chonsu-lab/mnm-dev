package com.mnm.api.farmer;

import com.mnm.api.farmer.DiaryDtos.DiaryDay;
import com.mnm.api.farmer.DiaryDtos.DiaryEntryDetail;
import com.mnm.api.farmer.DiaryDtos.DiaryEntryResponse;
import com.mnm.api.farmer.DiaryDtos.DiarySaveRequest;
import com.mnm.api.farmer.DiaryDtos.DiarySaveResponse;
import com.mnm.api.farmer.DiaryDtos.DiaryUpdateRequest;
import com.mnm.api.farmer.DiaryDtos.RecordDraft;
import com.mnm.api.farmer.DiaryDtos.TextRecordRequest;
import com.mnm.api.global.config.ApiPaths;
import com.mnm.api.global.response.BaseResponse;
import com.mnm.api.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(ApiPaths.FARMER)
@RequiredArgsConstructor
class DiaryController {

    private final DiaryService diaryService;

    @Operation(summary = "음성 기록 → 정리 초안 (FARM-03)",
            description = "multipart audio (webm·m4a, 60초 이하). 저장하지 않는다. 음성 인식이 안 되면 fallback=text_input")
    @PostMapping(value = "/records/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    BaseResponse<RecordDraft> voice(@AuthenticationPrincipal Jwt jwt, @RequestPart("audio") MultipartFile audio) {
        return BaseResponse.of(diaryService.draftFromVoice(FarmerSupport.userId(jwt), audio));
    }

    @Operation(summary = "글 기록 → 정리 초안 (FARM-25)", description = "저장하지 않는다. 정리가 안 되면 fallback=select_input")
    @PostMapping("/records/text")
    BaseResponse<RecordDraft> text(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TextRecordRequest request) {
        return BaseResponse.of(diaryService.draftFromText(FarmerSupport.userId(jwt), request.text()));
    }

    @Operation(summary = "일지 목록 (FARM-04)", description = "날짜별 묶음, 30일씩 최근순. next_cursor 를 cursor 로 넘긴다")
    @GetMapping("/diary")
    BaseResponse<PageResponse<DiaryDay>> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor) {
        return BaseResponse.of(diaryService.list(FarmerSupport.userId(jwt), cursor));
    }

    @Operation(summary = "일지 저장 (FARM-04)", description = "확인된 항목 저장. report 가 있으면 이력 신고서 초안도 만든다")
    @PostMapping("/diary")
    @ResponseStatus(HttpStatus.CREATED)
    BaseResponse<DiarySaveResponse> save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DiarySaveRequest request) {
        return BaseResponse.of(diaryService.save(FarmerSupport.userId(jwt), request));
    }

    @Operation(summary = "일지 기록 한 건 보기 (FARM-26)", description = "원래 말 · 함께 보내진 곳 포함")
    @GetMapping("/diary/{id}")
    BaseResponse<DiaryEntryDetail> get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return BaseResponse.of(diaryService.get(FarmerSupport.userId(jwt), id));
    }

    @Operation(summary = "일지 기록 고치기 (FARM-26)", description = "서버가 남긴 '신고' 기록은 고칠 수 없다")
    @PatchMapping("/diary/{id}")
    BaseResponse<DiaryEntryResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody DiaryUpdateRequest request) {
        return BaseResponse.of(diaryService.update(FarmerSupport.userId(jwt), id, request));
    }

    @Operation(summary = "일지 기록 지우기 (FARM-26)")
    @DeleteMapping("/diary/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        diaryService.delete(FarmerSupport.userId(jwt), id);
    }
}
