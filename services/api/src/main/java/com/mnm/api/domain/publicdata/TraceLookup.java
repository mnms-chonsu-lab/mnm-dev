package com.mnm.api.domain.publicdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 축산물이력제 조회 결과 캐시 (같은 번호는 하루 동안 다시 조회하지 않는다). */
@Entity
@Table(name = "trace_lookups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TraceLookup {

    @Id
    @Column(name = "ear_tag_no", length = 20)
    private String earTagNo;

    /** 이력제 API 응답 원문(XML) */
    @Column(columnDefinition = "text")
    private String response;

    /** ok | notfound | fail */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;
}
