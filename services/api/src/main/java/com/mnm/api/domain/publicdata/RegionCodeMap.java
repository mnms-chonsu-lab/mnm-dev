package com.mnm.api.domain.publicdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 법정동 코드표. */
@Entity
@Table(name = "region_code_map")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionCodeMap {

    /** 법정동 10자리 */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "region_code", length = 10)
    private String regionCode;

    /** sido | sigungu | emd | ri */
    @Column(name = "region_level", nullable = false, length = 10)
    private String regionLevel;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sido_code", nullable = false, length = 2)
    private String sidoCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sigungu_code", length = 5)
    private String sigunguCode;

    @Column(name = "sido_name", nullable = false, length = 30)
    private String sidoName;

    @Column(name = "sigungu_name", length = 30)
    private String sigunguName;

    @Column(name = "emd_name", length = 30)
    private String emdName;

    /** 폐지된 코드는 false */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
