package com.mnm.api.domain.farm;

import com.mnm.api.domain.org.ReceiverOrg;
import com.mnm.api.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 농장. address는 개인정보. */
@Entity
@Table(name = "farm_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "farm_id")
    private Long id;

    /** 농장식별번호 6자리. 농가 로그인 키 */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "farm_no", nullable = false, unique = true, length = 6)
    private String farmNo;

    /** 농가 계정 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    private String address;

    /** 법정동 10자리 */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "region_code", length = 10)
    private String regionCode;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(name = "head_count")
    private Integer headCount;

    /** 담당 축협 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id")
    private ReceiverOrg org;
}
