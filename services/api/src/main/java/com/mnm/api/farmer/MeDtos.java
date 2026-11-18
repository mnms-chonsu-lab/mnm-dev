package com.mnm.api.farmer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

/** 알림 (FARM-18 · 19), 내 정보·설정 (FARM-20) 요청·응답 */
final class MeDtos {

    private MeDtos() {
    }

    /**
     * kind: diff(평소와 다름) | deadline(기한) | result(접수자 처리 결과). ref_id: diff → cow_id, deadline → tasks_id,
     * result → deliveries_id. feedback: seen(확인함) | ok(문제 없음) | null
     */
    record AlertResponse(Long alertsId, String kind, String title, String body, Long refId, String feedback,
            Instant readAt, Instant createdAt) {
    }

    record FeedbackRequest(
            @NotBlank(message = "확인함 또는 문제 없음을 골라 주세요.")
            @Pattern(regexp = "seen|ok", message = "확인함 또는 문제 없음을 골라 주세요.") String feedback) {
    }

    /** font_step: 글자 크기 0~4 (작게 ~ 가장 크게), high_contrast: 선명한 화면, auto_speak: 소리 안내 자동 */
    record SettingsResponse(short fontStep, boolean highContrast, boolean autoSpeak) {
    }

    /** 보낸 값만 바꾼다 */
    record SettingsRequest(
            @Min(value = 0, message = "글자 크기를 다시 골라 주세요.") @Max(value = 4, message = "글자 크기를 다시 골라 주세요.") Short fontStep,
            Boolean highContrast,
            Boolean autoSpeak) {
    }

    /** cow_count: 목록에 있는 소, sent_count: 보낸 기록 수 */
    record FarmResponse(String farmNo, String farmName, String address, String ownerName, String phone,
            Integer headCount, String orgName, long cowCount, long sentCount) {
    }

    /** F-P06 연락처 바꾸기. 바꾼 번호로 다음에 로그인한다 */
    record FarmUpdateRequest(
            @NotBlank(message = "휴대폰 번호를 넣어 주세요.")
            @Pattern(regexp = "01\\d-?\\d{3,4}-?\\d{4}", message = "휴대폰 번호를 다시 확인해 주세요.") String phone) {
    }
}
