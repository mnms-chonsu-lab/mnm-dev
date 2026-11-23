package com.mnm.api.farmer;

import com.mnm.api.farmer.HistoryReportDtos.HistoryReportResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 말·글 기록 → 초안 (FARM-03·25), 농장 일지 (FARM-04·26) 요청·응답 */
final class DiaryDtos {

    /** 라우터가 내는 일지 분류 9개 (DB 에는 서버가 자동으로 쓰는 "신고"가 하나 더 있다) */
    static final String CATEGORY_REGEX = "사육현황|접종|소독|관찰|출입|출생|폐사|이동|메모";

    private DiaryDtos() {
    }

    /** FARM-25 글로 쓴 기록 */
    record TextRecordRequest(
            @NotBlank(message = "기록할 내용을 써 주세요.")
            @Size(max = 2000, message = "2000자까지 쓸 수 있어요.") String text) {
    }

    /**
     * FARM-03·25 결과. 저장하지 않은 초안이다 (F-S03 "이렇게 적을까요?" 확인 후 FARM-04 로 저장).
     * report_type: 출생 | 폐사 | 양도 | 양수 | 이동 | null. report_notice 는 신고 기한 안내 (발생일을 알 때).
     * fallback: null | text_input(음성을 못 알아들음 → 글로 쓰기) | select_input(정리하지 못함 → 선택형 입력).
     * audio_media_id: 말로 기록(FARM-03)의 음성 원본. FARM-04 저장 때 그대로 보낸다 (저장 못 했으면 null)
     */
    record RecordDraft(String rawText, String intent, List<DraftItem> items, String reportType,
            Map<String, Object> reportFields, List<String> missing, ReportNotice reportNotice, String fallback,
            Long audioMediaId) {

        RecordDraft(String rawText, String intent, List<DraftItem> items, String reportType,
                Map<String, Object> reportFields, List<String> missing, ReportNotice reportNotice, String fallback) {
            this(rawText, intent, items, reportType, reportFields, missing, reportNotice, fallback, null);
        }

        RecordDraft withAudio(Long id) {
            return new RecordDraft(rawText, intent, items, reportType, reportFields, missing, reportNotice, fallback, id);
        }
    }

    /** cow_id 는 끝 4자리로 찾은 내 소 (없으면 null) */
    record DraftItem(String category, String value, String cowTag4, Long cowId, String vaccineType, Integer headCount) {
    }

    record ReportNotice(LocalDate reportDue, Long dDay) {
    }

    /** FARM-04 저장. date 가 없으면 오늘(한국 시각). report 가 있으면 신고서 초안도 만든다 */
    record DiarySaveRequest(
            LocalDate date,
            @Size(max = 2000, message = "2000자까지 쓸 수 있어요.") String rawText,
            @NotEmpty(message = "저장할 기록이 없어요.") List<@Valid DiaryItemRequest> items,
            @Valid ReportRequest report,
            Long audioMediaId) {
    }

    record DiaryItemRequest(
            @NotBlank(message = "어떤 기록인지 골라 주세요.")
            @Pattern(regexp = CATEGORY_REGEX, message = "어떤 기록인지 골라 주세요.") String category,
            @Size(max = 500, message = "500자까지 쓸 수 있어요.") String value,
            Long cowId,
            @Size(max = 50, message = "50자까지 쓸 수 있어요.") String vaccineType,
            @PositiveOrZero(message = "두수를 다시 확인해 주세요.") Integer headCount) {
    }

    /** 신고서 초안. type: 출생 | 폐사 | 양도 | 양수 | 이동, fields 는 FARM-06 과 같은 키 */
    record ReportRequest(
            @NotBlank(message = "신고 종류를 골라 주세요.")
            @Pattern(regexp = "출생|폐사|양도|양수|이동", message = "신고 종류를 골라 주세요.") String type,
            Map<String, Object> fields) {
    }

    /** history_report: 신고서 초안을 만들었으면 FARM-06 과 같은 내용 (빈칸이 있으면 F-S04Q 로) */
    record DiarySaveResponse(List<Long> entryIds, HistoryReportResponse historyReport) {
    }

    /** FARM-04 목록: 날짜별 묶음 */
    record DiaryDay(LocalDate date, List<DiaryEntryResponse> entries) {
    }

    record DiaryEntryResponse(Long entryId, LocalDate date, String category, Long cowId, String cowTag4, String value,
            String vaccineType, Integer headCount) {
    }

    /**
     * FARM-26 상세. deliveries: 이 기록이 함께 보내진 곳 (보낸 날부터 14일 안의 일지가 함께 간다).
     * audio_url: 음성 원본 [듣기] 서명 URL (만료 10분). 원본이 없으면 null, 보관 기간이 지나 지워졌으면 열리지 않는다
     */
    record DiaryEntryDetail(DiaryEntryResponse entry, String rawText, String audioUrl, List<SentTo> deliveries) {
    }

    record SentTo(Long deliveryId, Instant approvedAt, String orgName, String receiverStatus) {
    }

    /** FARM-26 고치기. 보낸 값으로 바꾼다 */
    record DiaryUpdateRequest(
            LocalDate date,
            @NotBlank(message = "어떤 기록인지 골라 주세요.")
            @Pattern(regexp = CATEGORY_REGEX, message = "어떤 기록인지 골라 주세요.") String category,
            @Size(max = 500, message = "500자까지 쓸 수 있어요.") String value,
            Long cowId,
            @Size(max = 50, message = "50자까지 쓸 수 있어요.") String vaccineType,
            @PositiveOrZero(message = "두수를 다시 확인해 주세요.") Integer headCount) {
    }
}
