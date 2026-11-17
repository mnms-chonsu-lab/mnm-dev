package com.mnm.api.domain.farm;

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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 개체(소). */
@Entity
@Table(name = "cows")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cow_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    private FarmInfo farm;

    /** 귀표 끝 4자리 */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tag_last4", length = 4)
    private String tagLast4;

    /** 귀표 12자리. 미부착 송아지는 null */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tag_full", length = 12)
    private String tagFull;

    @Column(length = 30)
    private String breed;

    @Column(length = 10)
    private String sex;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "vaccine_last_date")
    private LocalDate vaccineLastDate;

    @Column(name = "vaccine_last_order")
    private Integer vaccineLastOrder;

    @Column(length = 50)
    private String barn;

    /** active | removed */
    @Column(nullable = false, length = 20)
    private String status = "active";

    /** trace(이력제 조회) | manual(직접 입력) */
    @Column(nullable = false, length = 10)
    private String source = "manual";

    @Column(name = "trace_farm_no", length = 20)
    private String traceFarmNo;

    @Column(name = "trace_checked_at")
    private Instant traceCheckedAt;
}
