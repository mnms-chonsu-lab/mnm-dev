package com.mnm.api.admin;

import com.mnm.api.admin.AdminDashboardDtos.Component;
import com.mnm.api.admin.AdminDashboardDtos.HealthResponse;
import com.mnm.api.admin.AdminDashboardDtos.TodayMetrics;
import com.mnm.api.domain.ops.EtlJobRun;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class AdminDashboardService {

    /** AI 상태를 보는 구간과 기준 (제안값, 실측으로 조정). 오류율 기준은 REQ-OPS-06 알람 기준과 같다 */
    static final Duration AI_WINDOW = Duration.ofHours(1);
    static final double AI_ERROR_RATE_LIMIT = 0.05;
    static final double AI_DELAY_MS_LIMIT = 3000;

    private final AdminMetricsRepository metricsRepository;

    /**
     * ADM-01 서비스 상태. DB 가 안 되면 나머지는 확인할 수 없어 unknown 으로 둔다.
     * 트랜잭션을 걸면 DB 가 안 될 때 연결 단계에서 실패하므로 쿼리마다 따로 연다
     */
    HealthResponse health() {
        Instant now = Instant.now();
        List<Component> components = new ArrayList<>();
        components.add(new Component("api", "normal", null, now));
        boolean dbUp = dbUp();
        components.add(new Component("db", dbUp ? "normal" : "error", null, now));
        if (!dbUp) {
            components.add(new Component("ai", "unknown", null, null));
            components.add(new Component("etl", "unknown", null, null));
            return new HealthResponse(components, now);
        }
        components.add(ai(now));
        components.add(etl());
        return new HealthResponse(components, now);
    }

    /** ADM-01 오늘 수치 */
    @Transactional(readOnly = true)
    TodayMetrics today() {
        LocalDate today = LocalDate.now(AdminSupport.KST);
        Instant since = today.atStartOfDay(AdminSupport.KST).toInstant();
        long records = metricsRepository.countDiary(null, today) + metricsRepository.countSymptomReports(null, since);
        Object[] ai = metricsRepository.inferenceStats(since).getFirst();
        long calls = ((Number) ai[0]).longValue();
        Double errorRate = calls == 0 ? null : ((Number) ai[1]).doubleValue() / calls;
        return new TodayMetrics(records, metricsRepository.countDeliveriesSince(since),
                metricsRepository.countPendingReceipts(), errorRate, since);
    }

    private boolean dbUp() {
        try {
            metricsRepository.ping();
            return true;
        } catch (RuntimeException e) {
            log.warn("DB 상태 확인 실패: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /** 최근 1시간 AI 호출의 오류율·평균 지연 */
    private Component ai(Instant now) {
        Object[] row = metricsRepository.inferenceStats(now.minus(AI_WINDOW)).getFirst();
        long calls = ((Number) row[0]).longValue();
        if (calls == 0) {
            return new Component("ai", "unknown", null, null);
        }
        double errorRate = ((Number) row[1]).doubleValue() / calls;
        double avgMs = row[2] == null ? 0 : ((Number) row[2]).doubleValue();
        String status = errorRate > AI_ERROR_RATE_LIMIT ? "error" : avgMs > AI_DELAY_MS_LIMIT ? "delayed" : "normal";
        return new Component("ai", status, Math.round(avgMs) + "ms", now);
    }

    /** 수집 작업별 마지막 실행 중 하나라도 실패면 오류. detail 은 실패한 작업 이름 */
    private Component etl() {
        List<EtlJobRun> runs = metricsRepository.latestEtlRuns();
        if (runs.isEmpty()) {
            return new Component("etl", "unknown", null, null);
        }
        List<String> failed = runs.stream().filter(r -> "failed".equals(r.getStatus())).map(EtlJobRun::getJob).toList();
        Instant lastAt = runs.stream()
                .map(r -> r.getFinishedAt() != null ? r.getFinishedAt() : r.getStartedAt())
                .max(Instant::compareTo).orElse(null);
        return new Component("etl", failed.isEmpty() ? "normal" : "error",
                failed.isEmpty() ? null : String.join(",", failed), lastAt);
    }
}
