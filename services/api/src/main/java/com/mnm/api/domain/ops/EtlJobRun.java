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

/** 공공데이터 수집 작업 실행 기록. */
@Entity
@Table(name = "etl_job_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EtlJobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "etl_job_run_id")
    private Long id;

    /** outbreak | disinfection_facility | farm_scale */
    @Column(nullable = false, length = 50)
    private String job;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** requested(어드민 [지금 수집]) | running | success | failed */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "rows")
    private Integer rows;

    @Column(columnDefinition = "text")
    private String error;

    /** 어드민 [지금 수집] 요청. 수집 서버의 예약 작업이 이 행을 이어받아 running → success/failed 로 바꾼다 */
    public static EtlJobRun request(String job) {
        EtlJobRun run = new EtlJobRun();
        run.job = job;
        run.startedAt = Instant.now();
        run.status = "requested";
        return run;
    }
}
