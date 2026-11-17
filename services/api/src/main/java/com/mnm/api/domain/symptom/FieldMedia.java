package com.mnm.api.domain.symptom;

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

/** 음성 · 사진 · 영상 파일. */
@Entity
@Table(name = "field_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "media_id")
    private Long id;

    /** 일지 음성은 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symptom_report_id")
    private SymptomReport symptomReport;

    /** audio | photo | video */
    @Column(nullable = false, length = 10)
    private String kind;

    @Column(name = "requested_part", length = 30)
    private String requestedPart;

    @Column(name = "predicted_part", length = 30)
    private String predictedPart;

    /** 요청 부위와 일치 여부. null = 확인 안 됨 */
    @Column(name = "match")
    private Boolean match;

    @Column(precision = 5, scale = 4)
    private BigDecimal score;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    /** Object Storage 경로 */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(precision = 6, scale = 2)
    private BigDecimal brightness;
}
