package com.mnm.api.farmer;

import com.mnm.api.farmer.DiaryDtos.DiaryEntryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 소 상태 확인 (FARM-10 · 11 · 14 · 16) 요청·응답 */
final class SymptomDtos {

    private SymptomDtos() {
    }

    /**
     * FARM-10 게시된 공식 증상 질문 (순서대로). answer_key 는 FARM-11 answers 의 키 (drool · blister · feed · lump).
     * yes_required_parts: "예"일 때 찍을 부위 코드 (mouth · hoof · flank · udder)
     */
    record QuestionResponse(Long ruleId, String answerKey, String text, String exampleImage,
            List<String> yesRequiredParts, String sourceDoc, String clause) {
    }

    /** 답: yes | no | unknown */
    record Answers(
            @NotBlank(message = "답을 골라 주세요.") @Pattern(regexp = "yes|no|unknown", message = "답을 골라 주세요.") String drool,
            @NotBlank(message = "답을 골라 주세요.") @Pattern(regexp = "yes|no|unknown", message = "답을 골라 주세요.") String blister,
            @NotBlank(message = "답을 골라 주세요.") @Pattern(regexp = "yes|no|unknown", message = "답을 골라 주세요.") String feed,
            @NotBlank(message = "답을 골라 주세요.") @Pattern(regexp = "yes|no|unknown", message = "답을 골라 주세요.") String lump) {

        Map<String, String> toMap() {
            return Map.of("drool", drool, "blister", blister, "feed", feed, "lump", lump);
        }
    }

    /**
     * FARM-11 소 상태 기록. cow_id 가 없으면 "귀표 번호를 모르겠어요".
     * observed_text 는 본 것 말하기(F-S08S), head_count · found_date · barn · dead_count 는 F-S09Q 빈칸 답.
     */
    record SymptomReportRequest(
            Long cowId,
            @NotNull(message = "답을 골라 주세요.") @Valid Answers answers,
            @Size(max = 1000, message = "1000자까지 쓸 수 있어요.") String observedText,
            @PositiveOrZero(message = "두수를 다시 확인해 주세요.") Integer headCount,
            LocalDate foundDate,
            @Size(max = 50, message = "50자까지 쓸 수 있어요.") String barn,
            @PositiveOrZero(message = "두수를 다시 확인해 주세요.") Integer deadCount) {
    }

    /**
     * matched_count = "예"로 답한 공식 질문 수 (판정 아님). required_parts 가 비면 사진 없이 요약으로.
     * disease: FMD | LSD | null — FARM-16 에 그대로 넘길 지침 분기 값 (모두 아니요면 null, 안내 없음)
     */
    record SymptomReportResponse(Long symptomReportId, int matchedCount, List<String> matched,
            List<PartGuide> requiredParts, String disease) {
    }

    /** 찍을 부위와 게시된 촬영 안내 문구 (없으면 guide = null) */
    record PartGuide(String part, String partName, String guide, Long guideRuleId) {
    }

    /**
     * FARM-14 오늘 기록 요약 (F-S11 · F-S19). related_diary 는 같은 소 최근 7일.
     * form6 · form6_text 는 인터넷 신고서 6개 항목 (빈칸은 null · '(빈칸)'). delivered: 승인해서 보낸 적이 있으면 true (QR 표시)
     */
    record SymptomSummaryResponse(Long symptomReportId, SummaryCow cow, int matchedCount, List<String> matched,
            String observedText, Integer headCount, LocalDate foundDate, String barn,
            List<DiaryEntryResponse> relatedDiary, LatestVaccine vaccine, List<PhotoResult> photos, VideoResult video,
            String disease,
            boolean delivered, Map<String, Object> form6, String form6Text) {
    }

    record SummaryCow(Long cowId, String tagLast4, String breed, String sex, Integer ageYears, String barn) {
    }

    /** 최근 접종: 일지 "접종" 기록이 있으면 그 값, 없으면 이력제 백신 최종 접종일 */
    record LatestVaccine(LocalDate date, String value) {
    }

    /** match: 부위 확인 결과 (null = 아직 확인 중이거나 확인 안 됨). url: 원본 서명 URL (만료 10분) */
    record PhotoResult(Long mediaId, String part, Boolean match, String url) {
    }

    /** 행동 영상 (F-S10V). ratio: stand · lie · eat (%, 참고). null = 분류 중이거나 분류 안 됨 */
    record VideoResult(Long mediaId, String url, Map<String, Integer> ratio) {
    }

    /** FARM-16 질병별 지금 할 일 (게시된 조치 문구 원문, 순서대로 최대 3개) */
    record ActionsResponse(String disease, String guideName, List<GuideText> actions) {
    }

    record GuideText(Long ruleId, String text, String sourceDoc, String clause, LocalDate revisedAt) {
    }
}
