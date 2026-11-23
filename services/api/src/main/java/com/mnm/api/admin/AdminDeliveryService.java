package com.mnm.api.admin;

import com.mnm.api.domain.delivery.Delivery;
import com.mnm.api.domain.delivery.ReceiverCheck;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.global.response.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A-S04 신고·전달 현황 (ADM-06). 내용(요약·신고서 항목)은 읽지 않고 진행 상태만 보여 준다 */
@Service
@RequiredArgsConstructor
class AdminDeliveryService {

    private static final Instant NO_END = Instant.parse("9999-12-31T00:00:00Z");

    private final AdminDeliveryRepository deliveryRepository;
    private final AdminSupport support;

    /**
     * A-S04 표 한 줄.
     * kind: symptom(소 상태 기록) | birth · death · move 등 이력 신고서 종류.
     * route: call | coop | web | gov. org_name 은 받는 기관, cc_org_name 은 전화 경로일 때 함께 받는 담당 축협.
     * status(전달): delivered(도착) | queued(대기 중) | failed(실패).
     * receiver_status(처리): new | checking | done, 도착하지 않았으면 null
     */
    record DeliveryRow(Long deliveriesId, Instant approvedAt, Instant receivedAt, Long farmId, String farmerName,
            String kind, String route, String orgName, String ccOrgName, String status, String receiverStatus) {
    }

    @Transactional(readOnly = true)
    PageResponse<DeliveryRow> list(String route, String status, Long orgId, Instant from, Instant to, String cursor) {
        List<Delivery> rows = deliveryRepository.search(route, status, orgId, from == null ? Instant.EPOCH : from,
                to == null ? NO_END : to, AdminSupport.cursor(cursor), Limit.of(AdminSupport.PAGE_SIZE + 1));
        List<Long> ids = rows.stream().map(Delivery::getId).toList();
        Map<Long, String> checks = ids.isEmpty() ? Map.of()
                : deliveryRepository.latestChecks(ids).stream().collect(Collectors.toMap(
                        c -> c.getDelivery().getId(), ReceiverCheck::getStatus, (a, b) -> b));
        return AdminSupport.page(rows, AdminSupport.PAGE_SIZE, d -> toRow(d, checks), Delivery::getId);
    }

    /** [다시 보내기]: 대기·실패 건을 접수자 수신함에 보이게 한다. 이미 도착한 건은 CONFLICT */
    @Transactional
    DeliveryRow retry(Long adminId, Long deliveryId) {
        Delivery d = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "전달 없음"));
        if ("delivered".equals(d.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 도착한 전달");
        }
        d.markDelivered();
        support.audit(adminId, "retry", "delivery", d.getId());
        return toRow(d, Map.of());
    }

    private static DeliveryRow toRow(Delivery d, Map<Long, String> checks) {
        boolean symptom = d.getReportSummary() != null;
        FarmInfo farm = symptom ? d.getReportSummary().getSymptomReport().getFarm() : d.getHistoryReport().getFarm();
        String receiverStatus = "delivered".equals(d.getStatus()) ? checks.getOrDefault(d.getId(), "new") : null;
        return new DeliveryRow(d.getId(), d.getApprovedAt(), d.getReceivedAt(), farm.getId(), farm.getOwner().getName(),
                symptom ? "symptom" : d.getHistoryReport().getType(), d.getRoute(), name(d.getOrg()),
                name(d.getCcOrg()), d.getStatus(), receiverStatus);
    }

    private static String name(ReceiverOrg org) {
        return org == null ? null : org.getName();
    }
}
