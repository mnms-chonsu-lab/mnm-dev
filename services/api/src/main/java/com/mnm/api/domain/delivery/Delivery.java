package com.mnm.api.domain.delivery;

import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.domain.report.HistoryReport;
import com.mnm.api.domain.report.ReportSummary;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;

/**
 * 전달 (농가 승인 이력). 농가가 승인한 기록만 접수자에게 전달한다.
 * reportSummary와 historyReport 중 정확히 하나만 값이 있다 (DB CHECK).
 */
@Entity
@Table(name = "deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deliveries_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_summary_id")
    private ReportSummary reportSummary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "history_report_id")
    private HistoryReport historyReport;

    /** call(전화) | coop(축협) | web(인터넷 신고서) | gov(시군) */
    @Column(nullable = false, length = 10)
    private String route;

    /** 받는 기관 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id")
    private ReceiverOrg org;

    /** 전화 경로일 때 함께 받는 담당 축협 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cc_org_id")
    private ReceiverOrg ccOrg;

    @Column(name = "share_phone", nullable = false)
    private boolean sharePhone = true;

    /** 같은 신고를 두 번 전달하지 않기 위한 키 */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    /** 농가가 누른 시각 */
    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;

    /** 서버 도착 시각 */
    @Generated
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** delivered | queued | failed */
    @Column(nullable = false, length = 20)
    private String status = "queued";

    /** 농가가 승인한 전달 1건. reportSummary 와 historyReport 중 하나만 넣는다 */
    public static Delivery approve(ReportSummary reportSummary, HistoryReport historyReport, String route,
            ReceiverOrg org, ReceiverOrg ccOrg, boolean sharePhone, String idempotencyKey, Instant approvedAt,
            String status) {
        Delivery delivery = new Delivery();
        delivery.reportSummary = reportSummary;
        delivery.historyReport = historyReport;
        delivery.route = route;
        delivery.org = org;
        delivery.ccOrg = ccOrg;
        delivery.sharePhone = sharePhone;
        delivery.idempotencyKey = idempotencyKey;
        delivery.approvedAt = approvedAt;
        delivery.status = status;
        return delivery;
    }

    /** 어드민 [다시 보내기] (ADM-06): 대기·실패 건을 접수자 수신함에 보이게 한다 */
    public void markDelivered() {
        this.status = "delivered";
    }
}
