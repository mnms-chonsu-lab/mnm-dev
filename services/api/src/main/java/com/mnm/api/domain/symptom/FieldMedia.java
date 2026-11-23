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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** 경로는 media_id 가 생긴 뒤 정하므로 처음에는 이 값으로 넣는다 */
    public static final String PENDING_PATH = "pending";

    /** 업로드 URL 발급(FARM-12)·음성 저장 때 먼저 만든다. 일지 음성은 report = null */
    public static FieldMedia create(SymptomReport report, String kind, String requestedPart) {
        FieldMedia media = new FieldMedia();
        media.symptomReport = report;
        media.kind = kind;
        media.requestedPart = requestedPart;
        media.filePath = PENDING_PATH;
        return media;
    }

    /** 다시 찍은 사진은 부위별 마지막 1장만 보여 준다 (#37). photos 는 id 오름차순, 부위는 처음 찍은 순서 */
    public static List<FieldMedia> latestPerPart(List<FieldMedia> photos) {
        Map<String, FieldMedia> last = new LinkedHashMap<>();
        photos.forEach(m -> last.put(m.requestedPart, m));
        return List.copyOf(last.values());
    }

    public void placeAt(String path) {
        this.filePath = path;
    }

    /** 부위 확인 결과 (/ai/part-check). 실패하면 부르지 않아 null(확인 안 됨)로 남는다 */
    public void recordPartCheck(String predictedPart, boolean match, BigDecimal score, String modelVersion,
            BigDecimal brightness) {
        this.predictedPart = predictedPart;
        this.match = match;
        this.score = score;
        this.modelVersion = modelVersion;
        this.brightness = brightness;
    }

    /** 행동 영상 분류 모델 (/ai/behavior). 프레임별 결과는 behavior_record */
    public void recordModel(String modelVersion) {
        this.modelVersion = modelVersion;
    }
}
