package com.mnm.api.admin;

import com.mnm.api.domain.ops.InferenceLog;
import com.mnm.api.global.response.PageResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A-S07 AI 지표·호출 로그 (ADM-11). inference_log 는 AI 서버가 쓰고 여기서는 읽기만 한다 */
@Service
@RequiredArgsConstructor
class AdminAiService {

    /** 계산식 ⑪의 구간 */
    static final Duration WINDOW = Duration.ofHours(24);
    private static final Instant NO_END = Instant.parse("9999-12-31T00:00:00Z");

    private final AdminMetricsRepository metricsRepository;

    /**
     * A-S07 지표 한 줄. error_rate 는 0~1 (호출이 없으면 null), avg_latency_ms 는 반올림.
     * no_evidence(404 근거 없음) · bad_input(422 무음·열 수 없는 파일 등)은 실패가 아니지만 따로 본다
     */
    record FeatureMetrics(String feature, long calls, long failures, Double errorRate, Long avgLatencyMs,
            long noEvidence, long badInput) {
    }

    /**
     * ADM-11 지표. total 은 전체 기능 합계(feature = null). diff_ok_rate = 평소와 다름 알림 피드백 중 "문제 없음" 비율
     * (피드백이 없으면 null, 기간 제한 없음)
     */
    record AiMetrics(Instant since, FeatureMetrics total, List<FeatureMetrics> features, Double diffOkRate,
            long diffFeedbackCount) {
    }

    /** A-S07 호출 로그 한 줄. result_summary 는 AI 서버가 전화번호를 가린 요약(코드·개수)만 담는다 */
    record InferenceLogRow(Long inferenceLogId, Instant at, String feature, String modelVersion, String inputKind,
            String resultSummary, Integer latencyMs, String error, Short httpStatus) {
    }

    @Transactional(readOnly = true)
    AiMetrics metrics() {
        Instant since = Instant.now().minus(WINDOW);
        List<FeatureMetrics> features = metricsRepository.inferenceStatsByFeature(since).stream()
                .map(r -> metrics((String) r[0], r[1], r[2], r[3], r[4], r[5]))
                .toList();
        long calls = features.stream().mapToLong(FeatureMetrics::calls).sum();
        long failures = features.stream().mapToLong(FeatureMetrics::failures).sum();
        Long avg = calls == 0 ? null : Math.round(features.stream()
                .mapToDouble(f -> f.avgLatencyMs() == null ? 0 : (double) f.avgLatencyMs() * f.calls()).sum() / calls);
        FeatureMetrics total = new FeatureMetrics(null, calls, failures, rate(failures, calls), avg,
                features.stream().mapToLong(FeatureMetrics::noEvidence).sum(),
                features.stream().mapToLong(FeatureMetrics::badInput).sum());

        Object[] fb = metricsRepository.diffFeedback().getFirst();
        long seen = num(fb[0]);
        long ok = num(fb[1]);
        return new AiMetrics(since, total, features, rate(ok, seen + ok), seen + ok);
    }

    @Transactional(readOnly = true)
    PageResponse<InferenceLogRow> logs(String feature, Instant from, Instant to, String cursor) {
        List<InferenceLog> rows = metricsRepository.searchInferenceLogs(feature, from == null ? Instant.EPOCH : from,
                to == null ? NO_END : to, AdminSupport.cursor(cursor), Limit.of(AdminSupport.PAGE_SIZE + 1));
        return AdminSupport.page(rows, AdminSupport.PAGE_SIZE, AdminAiService::toRow, InferenceLog::getId);
    }

    private static FeatureMetrics metrics(String feature, Object calls, Object failures, Object avg, Object notFound,
            Object badInput) {
        long c = num(calls);
        long f = num(failures);
        return new FeatureMetrics(feature, c, f, rate(f, c), avg == null ? null : Math.round(((Number) avg).doubleValue()),
                num(notFound), num(badInput));
    }

    private static Double rate(long part, long whole) {
        return whole == 0 ? null : (double) part / whole;
    }

    private static long num(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static InferenceLogRow toRow(InferenceLog i) {
        return new InferenceLogRow(i.getId(), i.getAt(), i.getFeature(), i.getModelVersion(), i.getInputKind(),
                i.getResultSummary(), i.getLatencyMs(), i.getError(), i.getHttpStatus());
    }
}
