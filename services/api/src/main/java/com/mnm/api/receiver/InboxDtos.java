package com.mnm.api.receiver;

import com.mnm.api.global.response.PageResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

final class InboxDtos {

    private InboxDtos() {
    }

    /**
     * RCV-01. counts: all · new · checking · done (상단 배지 = new).
     * outbreak: 관할 시군구 최근 30일 발생 건수 (공공데이터)
     */
    record InboxResponse(Map<String, Long> counts, Outbreak outbreak, PageResponse<InboxItem> page) {
    }

    record Outbreak(long fmd, long lsd, Instant baseAt) {
    }

    /**
     * 기록 카드. kind: symptom(소 상태 기록) | history(이력 신고서 초안). status: new | checking | done.
     * route: call | coop | web | gov. history_type·due_date 는 이력 신고서만, cow_tag4·matched_count 는 소 상태 기록만
     */
    record InboxItem(Long deliveryId, String kind, String status, String result, String farmName, String address,
            String regionCode, String cowTag4, Integer matchedCount, String historyType, Long historyReportId,
            LocalDate dueDate, String route, Instant approvedAt, Instant receivedAt) {
    }

    /**
     * RCV-02 한 장 요약. kind 에 맞는 symptom 또는 history 중 하나만 값이 있다.
     * farm.phone 은 농가가 연락처를 함께 보낸 경우만 (아니면 null, phone_shared=false)
     */
    record InboxDetail(Long deliveryId, String kind, String status, String result, String route, Instant approvedAt,
            Instant receivedAt, FarmPart farm, SymptomPart symptom, HistoryPart history, FarmBasics basics,
            List<DiaryItem> diary, List<TimelineItem> timeline, List<GuideLink> guides) {
    }

    record FarmPart(Long farmId, String farmNo, String name, String ownerName, String address, Integer headCount,
            boolean phoneShared, String phone) {
    }

    /** card 는 승인 때 만든 요약 카드 원본 (만들지 못했으면 null). 사진 원본 주소는 저장소 연결 후 추가 */
    record SymptomPart(Long symptomReportId, CowPart cow, List<String> matchedSymptoms, Integer matchedCount,
            Instant foundAt, String barn, List<PhotoPart> photos, Map<String, Object> card) {
    }

    record CowPart(String tag4, String tagFull, String breed, String sex, LocalDate birthDate) {
    }

    /** 부위 일치 결과만 (질병 관련 표시 없음). match: null = 확인 안 됨 */
    record PhotoPart(Long mediaId, String requestedPart, String predictedPart, Boolean match) {
    }

    /** status: sent(부탁받음) | checking | done. missing 은 아직 빈 칸 */
    record HistoryPart(Long historyReportId, String type, Map<String, Object> fields, List<String> missing,
            LocalDate dueDate, Long dDay, String status) {
    }

    /** 농장 기본 정보: 일지에서 분류별 가장 최근 값 (외부 출입 = "출입" 분류) */
    record FarmBasics(DiaryItem vaccine, DiaryItem disinfection, DiaryItem visit) {
    }

    record DiaryItem(LocalDate date, String category, String value, String vaccineType, String cowTag4) {
    }

    /** 처리 이력 · 메모. 상태 변경과 메모 저장마다 1건 */
    record TimelineItem(String status, String result, String memo, Instant at, String userName, String orgName) {
    }

    record GuideLink(String sourceDoc, LocalDate revisedAt) {
    }

    /**
     * RCV-03. expected_status 는 화면에서 보던 상태 (다른 담당자가 먼저 바꿨으면 CONFLICT-409).
     * status=done 이면 result 필수 (inspection_request | vet_guide | observe_guide | not_applicable)
     */
    record StatusRequest(
            @NotBlank(message = "바꿀 상태를 골라 주세요.")
            @Pattern(regexp = "checking|done", message = "바꿀 상태를 골라 주세요.") String status,
            @NotBlank(message = "기록을 새로 고친 뒤 다시 시도해 주세요.")
            @Pattern(regexp = "new|checking|done", message = "기록을 새로 고친 뒤 다시 시도해 주세요.") String expectedStatus,
            @Pattern(regexp = "inspection_request|vet_guide|observe_guide|not_applicable",
                    message = "처리 결과를 골라 주세요.") String result,
            @Size(max = 1000, message = "메모는 1,000자까지 쓸 수 있어요.") String memo) {
    }

    /** RCV-04 통화·처리 메모 */
    record MemoRequest(
            @NotBlank(message = "메모를 적어 주세요.")
            @Size(max = 1000, message = "메모는 1,000자까지 쓸 수 있어요.") String memo) {
    }

    record StatusResponse(Long deliveryId, String status, String result, Instant at) {
    }

    /** RCV-05 신고서 6개 항목. text 는 [항목 전체 복사]용 줄바꿈 구분 텍스트 */
    record Form6Response(Long deliveryId, List<Form6Item> items, List<String> missing, String text) {
    }

    record Form6Item(String key, String label, String value) {
    }

    /**
     * RCV-10. fields: 빈칸 채우기 (농가가 비워 둔 칸과 출생의 송아지 귀표 calf_tag 만). done=true 면 대신 신고 마침.
     * 빈칸이 남아 있으면 마칠 수 없다 (VALID-422)
     */
    record ProxyRequest(Map<String, String> fields, boolean done) {
    }

    record ProxyResponse(Long deliveryId, HistoryPart history, String status, LocalDate earTagDue) {
    }
}
