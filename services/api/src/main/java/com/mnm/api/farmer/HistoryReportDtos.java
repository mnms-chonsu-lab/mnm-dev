package com.mnm.api.farmer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 이력 신고서 (FARM-06~09) 요청·응답 */
final class HistoryReportDtos {

    private HistoryReportDtos() {
    }

    /**
     * FARM-06 신고서 초안. type: 출생 | 폐사 | 양도 | 양수 | 이동. status: draft | sent | checking | done.
     * fields 키 — 출생: mother_tag4 · birth_date · sex · breed · calf_tag(선택) / 폐사: cow_tag · death_date /
     * 양도·양수·이동: cow_tag · move_date · counterpart_farm_no · counterpart_name.
     * missing 이 비어야 can_submit (직접 제출). 빈칸이 있어도 축협 부탁은 된다.
     */
    record HistoryReportResponse(Long historyReportId, String type, Map<String, Object> fields, List<String> missing,
            String nextQuestion, boolean canSubmit, LocalDate dueDate, Long dDay, String mode, String status,
            List<ReportCow> cows, ReportFarm farm) {
    }

    /** 신고서 칸(mother_tag4 · cow_tag)이 가리키는 내 소 */
    record ReportCow(String field, Long cowId, String tagLast4, String tagFull, String breed, String sex,
            LocalDate birthDate, Integer ageYears) {
    }

    /** 신고인·농장 (계정 정보로 자동) */
    record ReportFarm(String ownerName, String farmName, String farmNo, String address, String orgName) {
    }

    /** FARM-07 빈칸 채우기. 보낸 칸만 바꾸고 null 은 지운다. type 은 양도·양수·이동끼리만 바꿀 수 있다 */
    record ReportPatchRequest(
            @Pattern(regexp = "양도|양수|이동", message = "구분을 다시 골라 주세요.") String type,
            @NotNull(message = "고칠 내용을 넣어 주세요.") Map<String, Object> fields) {
    }

    /** FARM-08 직접 제출용 항목. text 는 줄바꿈으로 나눈 복사용 텍스트 (빈칸은 '(빈칸)') */
    record CopyTextResponse(String title, List<CopyLine> lines, String text, boolean canSubmit) {
    }

    record CopyLine(String label, String value) {
    }

    /** FARM-09 mode: self(신고 마침) | coop(축협에 부탁) */
    record SubmitRequest(
            @NotBlank(message = "제출 방식을 골라 주세요.")
            @Pattern(regexp = "self|coop", message = "제출 방식을 골라 주세요.") String mode) {
    }

    /** ear_tag_due: 출생 신고를 마쳤고 송아지 귀표가 아직 없으면 귀표 기한 (할 일로 등록됨) */
    record SubmitResponse(Long historyReportId, String status, String mode, Long deliveryId, String orgName,
            LocalDate earTagDue) {
    }
}
