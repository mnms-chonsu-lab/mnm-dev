package com.mnm.api.domain.publicdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 거점소독시설 (공공데이터). 컬럼명은 원본 이름을 따른다. */
@Entity
@Table(name = "disinfection_facility")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisinfectionFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "disinfection_facility_id")
    private Long id;

    /** 거점소독장소명 */
    @Column(name = "pstn_dsnf_plc_nm", nullable = false, length = 100)
    private String name;

    @Column(name = "addr")
    private String address;

    /** 운영시간 */
    @Column(name = "oper_hr", length = 100)
    private String operatingHours;

    /** 담당자 전화번호 */
    @Column(name = "pic_telno", length = 20)
    private String phone;

    /** 경도 */
    @Column(name = "lot", precision = 10, scale = 7)
    private BigDecimal lng;

    /** 위도 */
    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;
}
