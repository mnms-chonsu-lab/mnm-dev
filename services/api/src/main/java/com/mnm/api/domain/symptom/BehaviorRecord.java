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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public static BehaviorRecord of(FieldMedia media, Cow cow, String label, BigDecimal confidence, Instant observedAt,
            String modelVersion) {
        BehaviorRecord record = new BehaviorRecord();
        record.media = media;
        record.cow = cow;
        record.label = label;
        record.confidence = confidence;
        record.observedAt = observedAt;
        record.modelVersion = modelVersion;
        return record;
    }

    /** 서기·눕기·먹기 비율(%, 박스 수 기준, /ai/behavior summary 와 같은 계산). 결과가 없으면 null */
    public static Map<String, Integer> ratio(List<BehaviorRecord> records) {
        if (records.isEmpty()) {
            return null;
        }
        Map<String, Integer> ratio = new LinkedHashMap<>();
        for (String label : List.of("stand", "lie", "eat")) {
            long count = records.stream().filter(r -> label.equals(r.getLabel())).count();
            ratio.put(label, (int) Math.round(count * 100.0 / records.size()));
        }
        return ratio;
    }
}
