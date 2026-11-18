package com.mnm.api.domain.symptom;

import com.mnm.api.domain.common.BaseTimeEntity;
import com.mnm.api.domain.farm.Cow;
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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 소 상태 기록 · 문진. */
@Entity
@Table(name = "symptom_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SymptomReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "symptom_report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    private FarmInfo farm;

    /** null = 개체 미확인 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cow_id")
    private Cow cow;

    /** JSON 문자열: {drool, blister, feed, nodule} */
    @JdbcTypeCode(SqlTypes.JSON)
    private String answers;

    /** 공식 증상 질문에 "예"로 답한 수 */
    @Column(name = "matched_count")
    private Short matchedCount;

    @Column(name = "found_at")
    private Instant foundAt;

    @Column(length = 50)
    private String barn;

    /** 소 상태 기록 저장 (FARM-11). answers 는 JSON 문자열, matchedCount 는 "예"로 답한 공식 질문 수 */
    public static SymptomReport create(FarmInfo farm, Cow cow, String answers, short matchedCount, Instant foundAt,
            String barn) {
        SymptomReport report = new SymptomReport();
        report.farm = farm;
        report.cow = cow;
        report.answers = answers;
        report.matchedCount = matchedCount;
        report.foundAt = foundAt;
        report.barn = barn;
        return report;
    }
}
