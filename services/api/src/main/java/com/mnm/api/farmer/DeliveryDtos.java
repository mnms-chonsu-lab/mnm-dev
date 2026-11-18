package com.mnm.api.farmer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

/** 승인 → 전달 (FARM-15), 내가 보낸 기록 (FARM-27) 요청·응답 */
final class DeliveryDtos {

    private DeliveryDtos() {
    }

    /**
     * FARM-15. route: call(전화 1588-9060) | coop(우리 축협 방역팀) | web(인터넷 신고서) | gov(시·군 가축방역관).
     * send_to_coop: 전화·인터넷 경로에서 담당 축협에도 보내기 (기본 켬). share_phone: 휴대폰 번호 함께 보내기 (기본 켬).
     * approved_at: 농가가 [네, 보낼게요]를 누른 시각 (대기열에서 나중에 보낼 때 원래 시각, 없으면 서버 시각).
     */
    record DeliveryRequest(
            @NotNull(message = "보낼 기록을 골라 주세요.") Long symptomReportId,
            @NotBlank(message = "신고 방법을 골라 주세요.")
            @Pattern(regexp = "call|coop|web|gov", message = "신고 방법을 골라 주세요.") String route,
            Boolean sendToCoop,
            Boolean sharePhone,
            Instant approvedAt) {
    }

    /**
     * status: delivered(수신함에 도착) | not_sent(전화·인터넷 경로에서 축협 보내기를 끔 → 일지에만 남김).
     * disease 는 FARM-16 에 넘길 지침 분기 값, form6_text 는 인터넷 신고서 복사 텍스트 (web 경로)
     */
    record DeliveryResponse(Long deliveryId, String route, String status, String orgName, String ccOrgName,
            Instant approvedAt, String disease, String form6Text) {
    }

    /**
     * FARM-27 보낸 기록. kind: symptom(소 상태 기록) | history(이력 신고서).
     * receiver_status: new | checking | done (접수자 처리 상태), receiver_result: 처리 결과 코드 (없으면 null)
     */
    record SentRecordResponse(Long deliveryId, Instant approvedAt, String kind, String title, String route,
            String orgName, String status, String receiverStatus, String receiverResult, Long symptomReportId,
            Long historyReportId) {
    }
}
