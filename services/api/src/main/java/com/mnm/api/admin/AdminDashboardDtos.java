package com.mnm.api.admin;

import java.time.Instant;
import java.util.List;

/** 운영 대시보드 응답 (ADM-01, A-S01) */
final class AdminDashboardDtos {

    private AdminDashboardDtos() {
    }

    /** 구성요소별 상태. 행을 누르면 해당 관리 화면으로 간다 */
    record HealthResponse(List<Component> components, Instant checkedAt) {
    }

    /**
     * key: api | db | ai | etl. status: normal(정상) | delayed(지연) | error(오류) | unknown(기록 없음).
     * detail: 화면에 덧붙이는 값 (예: AI 평균 지연 ms, 실패한 수집 작업 이름). last_at: 그 구성요소의 마지막 확인·실행 시각
     */
    record Component(String key, String status, String detail, Instant lastAt) {
    }

    /** 오늘(한국 시간 0시부터) 수치. ai_error_rate 는 0~1, 호출이 없으면 null */
    record TodayMetrics(long records, long deliveries, long pendingReceipts, Double aiErrorRate, Instant since) {
    }
}
