package com.mnm.api.admin;

import com.mnm.api.domain.ops.EtlJobRun;
import com.mnm.api.global.error.BusinessException;
import com.mnm.api.global.error.ErrorCode;
import com.mnm.api.global.response.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A-S06 공공데이터 수집 현황 (ADM-09). 수집은 mnm-data ETL 이 서버 예약 작업으로 돌리고 etl_job_run 에 기록한다.
 * [지금 수집]은 requested 행만 넣고, 예약 작업(5분마다)이 그 행을 이어받아 실행한다 (mnm-data PR #8 PM 결정)
 */
@Service
@RequiredArgsConstructor
class AdminEtlService {

    /** 작업 이름 → 주기 (daily | monthly). 이름은 mnm-data etl/run_requested.py 의 SCRIPTS 와 같다 */
    static final Map<String, String> JOBS = Map.of(
            "outbreak", "daily",
            "disinfection_facility", "daily",
            "farm_scale", "monthly");
    private static final List<String> ORDER = List.of("outbreak", "farm_scale", "disinfection_facility");
    private static final List<String> BUSY = List.of("requested", "running");

    private final AdminEtlRepository etlRepository;
    private final AdminMetricsRepository metricsRepository;
    private final AdminSupport support;

    /** 실행 기록 한 줄. status: requested | running | success | failed */
    record RunRow(Long etlJobRunId, String job, String status, Instant startedAt, Instant finishedAt, Integer rows,
            String error) {
    }

    /** A-S06 표 한 줄. busy 면 [지금 수집]을 막는다. 기록이 없으면 last_run · last_success 가 null */
    record JobRow(String job, String schedule, RunRow lastRun, RunRow lastSuccess, boolean busy) {
    }

    @Transactional(readOnly = true)
    List<JobRow> jobs() {
        Map<String, EtlJobRun> last = byJob(metricsRepository.latestEtlRuns());
        Map<String, EtlJobRun> success = byJob(etlRepository.lastSuccesses());
        Set<String> busy = Set.copyOf(etlRepository.busyJobs());
        return ORDER.stream().map(job -> new JobRow(job, JOBS.get(job), row(last.get(job)), row(success.get(job)),
                busy.contains(job))).toList();
    }

    @Transactional(readOnly = true)
    PageResponse<RunRow> runs(String job, String cursor) {
        known(job);
        List<EtlJobRun> rows = etlRepository.runs(job, AdminSupport.cursor(cursor), Limit.of(AdminSupport.PAGE_SIZE + 1));
        return AdminSupport.page(rows, AdminSupport.PAGE_SIZE, AdminEtlService::row, EtlJobRun::getId);
    }

    /** [지금 수집]. 같은 작업이 요청됐거나 실행 중이면 CONFLICT (중복 실행 방지) */
    @Transactional
    RunRow request(Long adminId, String job) {
        known(job);
        if (etlRepository.existsByJobAndStatusIn(job, BUSY)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 요청됐거나 실행 중인 수집 작업");
        }
        EtlJobRun run = etlRepository.save(EtlJobRun.request(job));
        support.audit(adminId, "run", "etl_job_run", run.getId());
        return row(run);
    }

    private static void known(String job) {
        if (!JOBS.containsKey(job)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "없는 수집 작업");
        }
    }

    private static Map<String, EtlJobRun> byJob(List<EtlJobRun> runs) {
        return runs.stream().collect(Collectors.toMap(EtlJobRun::getJob, Function.identity(), (a, b) -> b));
    }

    private static RunRow row(EtlJobRun e) {
        return e == null ? null : new RunRow(e.getId(), e.getJob(), e.getStatus(), e.getStartedAt(), e.getFinishedAt(),
                e.getRows(), e.getError());
    }
}
