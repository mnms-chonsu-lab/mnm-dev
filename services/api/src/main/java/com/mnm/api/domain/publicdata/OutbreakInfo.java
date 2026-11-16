package com.mnm.api.domain.publicdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 가축 질병 발생 정보 (공공데이터). 컬럼명은 원본 이름을 따른다. */
@Entity
@Table(name = "outbreak_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutbreakInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbreak_info_id")
    private Long id;

    /** 원본 발생 번호 */
    @Column(name = "ictsd_occrrnc_no", nullable = false, unique = true, length = 100)
    private String occurrenceNo;

    /** 가축전염병명 */
    @Column(name = "lknts_nm", nullable = false, length = 50)
    private String diseaseName;

    /** 농장 소재지 */
    @Column(name = "farm_locplc")
    private String farmLocation;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "region_code", length = 10)
    private String regionCode;

    /** 발생일 */
    @Column(name = "occrrnc_de")
    private LocalDate occurredOn;

    /** 축종 */
    @Column(name = "lvstckspc_nm", length = 50)
    private String livestockType;

    /** 발생 두수 */
    @Column(name = "occrrnc_lvstckcnt")
    private Integer livestockCount;

    /** 진단기관 */
    @Column(name = "dgnss_engn_nm", length = 100)
    private String diagnosisOrg;

    /** 종식일 */
    @Column(name = "cessation_de")
    private LocalDate ceasedOn;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;
}
