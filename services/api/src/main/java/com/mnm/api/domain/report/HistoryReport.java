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
}
