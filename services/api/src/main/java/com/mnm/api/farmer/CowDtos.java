package com.mnm.api.farmer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

/** 내 소 (FARM-02 · 22 · 23 · 24) 요청·응답 */
final class CowDtos {

    private CowDtos() {
    }

    /** source: trace(이력제 조회값) | manual(직접 입력). status: active | removed */
    record CowResponse(Long cowId, String tagLast4, String tagFull, String breed, String sex, LocalDate birthDate,
            Integer ageYears, String barn, LocalDate vaccineLastDate, Integer vaccineLastOrder, String source,
            Instant traceCheckedAt, String status) {
    }

    /** FARM-22 이력제 조회 결과 (값은 가공하지 않음). already_registered 면 등록 시 CONFLICT-409 */
    record LookupResponse(String tagNo, String breed, String sex, LocalDate birthDate, LocalDate vaccineLastDate,
            Integer vaccineLastOrder, boolean alreadyRegistered) {
    }

    /**
     * FARM-23 소 등록. 이력제에서 찾으면 그 값으로, 못 찾거나 조회가 안 되면 sex·birth_date 직접 입력으로 등록.
     * birth_date 는 YYYY-MM 또는 YYYY-MM-DD (일을 모르면 1일로 저장). 다른 농장 소면 confirm_other_farm=true 로 다시 보낸다.
     */
    record CowRegisterRequest(
            @NotBlank(message = "귀표에 적힌 12자리 숫자를 넣어 주세요.")
            @Pattern(regexp = "[\\d\\s-]{12,16}", message = "귀표에 적힌 12자리 숫자를 넣어 주세요.") String tagNo,
            @Size(max = 50, message = "50자까지 쓸 수 있어요.") String barn,
            @Pattern(regexp = "암|수", message = "성별을 골라 주세요.") String sex,
            @Pattern(regexp = "\\d{4}-\\d{2}(-\\d{2})?", message = "출생 연도와 월을 넣어 주세요.") String birthDate,
            Boolean confirmOtherFarm) {

        /** Jackson 3 은 빠진 boolean 을 오류로 보므로 Boolean 으로 받고 없으면 false */
        boolean otherFarmConfirmed() {
            return Boolean.TRUE.equals(confirmOtherFarm);
        }
    }

    /**
     * FARM-24 축사 수정 · 목록에서 빼기 · 이력제 다시 불러오기. barn 은 보낼 때만 바꾼다(빈 문자열 = 지움).
     * remove_reason(팔림 · 폐사 · 기타)을 보내면 목록에서 뺀다. create_report 면 폐사 → 폐사 신고서, 팔림 → 양도 신고서 초안.
     * refresh_trace 면 귀표로 이력제를 다시 조회해 그 값으로 바꾼다 (직접 입력한 소, F-P05)
     */
    record CowUpdateRequest(
            @Size(max = 50, message = "50자까지 쓸 수 있어요.") String barn,
            @Pattern(regexp = "팔림|폐사|기타", message = "빼는 이유를 골라 주세요.") String removeReason,
            Boolean createReport,
            Boolean refreshTrace) {

        boolean reportRequested() {
            return Boolean.TRUE.equals(createReport);
        }

        boolean traceRefreshRequested() {
            return Boolean.TRUE.equals(refreshTrace);
        }
    }

    /** history_report_id: create_report 로 만든 신고서 초안 (F-S04D · F-S04M) */
    record CowUpdateResponse(CowResponse cow, Long historyReportId) {
    }
}
