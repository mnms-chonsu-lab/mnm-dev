package com.mnm.api.domain.report;

import com.mnm.api.domain.common.BaseTimeEntity;
import com.mnm.api.domain.symptom.SymptomReport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 접수자용 한 장 요약. */
@Entity
@Table(name = "report_summary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportSummary extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_summary_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "symptom_report_id", nullable = false)
    private SymptomReport symptomReport;

    /** JSON 문자열: 요약 카드 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "card_json")
    private String cardJson;

    /** JSON 문자열: 신고서 6개 항목 (시행규칙 제13조) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form6_json")
    private String form6Json;
}
