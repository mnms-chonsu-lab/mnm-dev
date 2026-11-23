package com.mnm.api.domain.ops;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;

/** AI 호출 로그. result_summary에 개인정보를 넣지 않는다. */
@Entity
@Table(name = "inference_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InferenceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inference_log_id")
    private Long id;

    @Column(nullable = false, length = 50)
    private String feature;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "input_kind", length = 30)
    private String inputKind;

    @Column(name = "result_summary", length = 500)
    private String resultSummary;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(columnDefinition = "text")
    private String error;

    /** AI 서버 응답 상태 (200·404·422·503·504). 칸이 생기기 전 기록은 null. 계산식 ⑪은 503·504만 실패로 센다 */
    @Column(name = "http_status")
    private Short httpStatus;

    @Generated
    @Column(nullable = false)
    private Instant at;
}
