package com.mnm.api.domain.publicdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 시군별 한우 사육규모 통계 (공공데이터, 시군 · 연도별 1행). farms = 농장 수, heads = 마릿수. */
@Entity
@Table(name = "farm_scale_statistics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmScaleStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "farm_scale_statistics_id")
    private Long id;

    @Column(name = "seq_no")
    private Integer seqNo;

    @Column(nullable = false, length = 20)
    private String breed;

    @Column(nullable = false, length = 20)
    private String province;

    @Column(name = "city_county", nullable = false, length = 30)
    private String cityCounty;

    @Column(name = "year", nullable = false)
    private short year;

    @Column(name = "total_farms", nullable = false)
    private int totalFarms;

    @Column(name = "total_heads", nullable = false)
    private int totalHeads;

    @Column(name = "farms_over_100", nullable = false)
    private int farmsOver100;

    @Column(name = "heads_over_100", nullable = false)
    private int headsOver100;

    @Column(name = "farms_50to100", nullable = false)
    private int farms50to100;

    @Column(name = "heads_50to100", nullable = false)
    private int heads50to100;

    @Column(name = "farms_30to50", nullable = false)
    private int farms30to50;

    @Column(name = "heads_30to50", nullable = false)
    private int heads30to50;

    @Column(name = "farms_7to30", nullable = false)
    private int farms7to30;

    @Column(name = "heads_7to30", nullable = false)
    private int heads7to30;

    @Column(name = "farms_under7", nullable = false)
    private int farmsUnder7;

    @Column(name = "heads_under7", nullable = false)
    private int headsUnder7;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "region_code", length = 10)
    private String regionCode;

    /** 기준 시점 'YYYY.01' */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "reference_ym", nullable = false, length = 7)
    private String referenceYm;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;
}
