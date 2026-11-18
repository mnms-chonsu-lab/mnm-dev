package com.mnm.api.domain.report;

import com.mnm.api.domain.farm.FarmInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 이력 신고서 초안 (출생 · 폐사 · 이동). */
@Entity
@Table(name = "history_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HistoryReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    private FarmInfo farm;

    /** 출생 | 폐사 | 이동 등 */
    @Column(nullable = false, length = 20)
    private String type;

    /** JSON 문자열: 신고서 항목 */
    @JdbcTypeCode(SqlTypes.JSON)
    private String fields;

    /** JSON 문자열: 빠진 항목 목록 */
    @JdbcTypeCode(SqlTypes.JSON)
    private String missing;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** self(직접 제출) | coop(축협 부탁) */
    @Column(length = 10)
    private String mode;

    /** draft | sent | checking | done */
    @Column(nullable = false, length = 20)
    private String status = "draft";

    /** 일지에서 만든 신고서 초안. fields·missing 은 JSON 문자열 */
    public static HistoryReport draft(FarmInfo farm, String type, String fields, String missing, LocalDate dueDate) {
        HistoryReport report = new HistoryReport();
        report.farm = farm;
        report.type = type;
        report.status = "draft";
        report.fillFields(fields, missing, dueDate);
        return report;
    }

    /** 빈칸 채우기 (FARM-07). 발생일이 바뀌면 다시 계산한 기한을 넣는다 */
    public void fillFields(String fields, String missing, LocalDate dueDate) {
        this.fields = fields;
        this.missing = missing;
        this.dueDate = dueDate;
    }

    /** 직접 제출: 농가가 이력제에 신고를 마쳤다고 표시 (FARM-09 mode=self) */
    public void finishSelf() {
        this.mode = "self";
        this.status = "done";
    }

    /** 축협에 부탁: 담당 축협 수신함으로 보냈다 (FARM-09 mode=coop) */
    public void sendToCoop() {
        this.mode = "coop";
        this.status = "sent";
    }

    public boolean isDraft() {
        return "draft".equals(status);
    }

    /** 초안의 양도 · 양수 · 이동 구분을 농가가 바꾼다 (F-S04M) */
    public void changeMoveType(String type) {
        this.type = type;
    }

    /** 축협 접수자가 확인을 시작했다 (RCV-03 · RCV-04). 부탁받은 신고서만 바뀐다 */
    public void startCoopCheck() {
        if ("sent".equals(status)) {
            this.status = "checking";
        }
    }

    /** 축협 접수자가 이력제에 대신 신고했다 (RCV-10) */
    public void finishByCoop() {
        this.status = "done";
    }

    public boolean isDone() {
        return "done".equals(status);
    }
}
