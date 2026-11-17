package com.mnm.api.domain.symptom;

import com.mnm.api.domain.farm.Cow;
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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 행동 영상 분류 결과 (관측 시점별). */
@Entity
@Table(name = "behavior_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BehaviorRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "behavior_record_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private FieldMedia media;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cow_id")
    private Cow cow;

    /** stand | lie | eat */
    @Column(nullable = false, length = 20)
    private String label;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "observed_at")
    private Instant observedAt;

    @Column(name = "model_version", length = 50)
    private String modelVersion;
}
