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

    /** 소 등록 (FARM-23). source = trace(이력제 조회값) | manual(직접 입력). 귀표 끝 4자리는 12자리의 마지막 4자리 */
    public static Cow register(FarmInfo farm, String tagFull, String breed, String sex, LocalDate birthDate,
            LocalDate vaccineLastDate, Integer vaccineLastOrder, String barn, String source, String traceFarmNo,
            Instant traceCheckedAt) {
        Cow cow = new Cow();
        cow.farm = farm;
        cow.fill(tagFull, breed, sex, birthDate, vaccineLastDate, vaccineLastOrder, barn, source, traceFarmNo,
                traceCheckedAt);
        return cow;
    }

    /** 목록에서 뺐던 소를 같은 귀표로 다시 등록한다 (같은 농장·귀표는 한 행만 둘 수 있다) */
    public void reRegister(String breed, String sex, LocalDate birthDate, LocalDate vaccineLastDate,
            Integer vaccineLastOrder, String barn, String source, String traceFarmNo, Instant traceCheckedAt) {
        fill(tagFull, breed, sex, birthDate, vaccineLastDate, vaccineLastOrder, barn, source, traceFarmNo,
                traceCheckedAt);
    }

    private void fill(String tagFull, String breed, String sex, LocalDate birthDate, LocalDate vaccineLastDate,
            Integer vaccineLastOrder, String barn, String source, String traceFarmNo, Instant traceCheckedAt) {
        this.tagFull = tagFull;
        this.tagLast4 = tagFull == null ? null : tagFull.substring(tagFull.length() - 4);
        this.breed = breed;
        this.sex = sex;
        this.birthDate = birthDate;
        this.vaccineLastDate = vaccineLastDate;
        this.vaccineLastOrder = vaccineLastOrder;
        this.barn = barn;
        this.source = source;
        this.traceFarmNo = traceFarmNo;
        this.traceCheckedAt = traceCheckedAt;
        this.status = "active";
    }

    public void changeBarn(String barn) {
        this.barn = barn;
    }

    /** 팔림·폐사 등으로 목록에서 뺀다. 지난 기록은 그대로 남는다 */
    public void remove() {
        this.status = "removed";
    }

    public boolean isActive() {
        return "active".equals(status);
    }
}
