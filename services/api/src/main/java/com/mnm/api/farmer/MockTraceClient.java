package com.mnm.api.farmer;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * 이력제 조회 가짜 구현. 화면의 모든 경우를 귀표 번호로 재현할 수 있게 정했다.
 * - 마지막 자리 0 → 못 찾음 (F-P04N 예시 "002 1234 5678 0")
 * - 008 로 시작 → 다른 농장 소 (농장식별번호 999999)
 * - 009 로 시작 → 조회 실패
 * - 나머지 → 한우, 번호로 정한 성별·출생일, 구제역 백신 7차
 * shortcut: 인교님 조회 모듈(REQ-DATA-11) 호출 방식·API 키가 정해지면 실제 구현과 trace_lookups 하루 캐시로 바꾼다.
 */
@Component
class MockTraceClient implements TraceClient {

    static final String OTHER_FARM_NO = "999999";

    @Override
    public TraceResult lookup(String tagNo) {
        if (tagNo.endsWith("0")) {
            return TraceResult.of(Status.NOT_FOUND);
        }
        if (tagNo.startsWith("009")) {
            return TraceResult.of(Status.FAILED);
        }
        int d = tagNo.charAt(10) - '0';
        LocalDate birth = LocalDate.of(2020 + d % 6, 1 + d, 1 + d * 2);
        return new TraceResult(Status.FOUND, "한우", d % 2 == 0 ? "암" : "수", birth, LocalDate.of(2026, 4, 2), 7,
                tagNo.startsWith("008") ? OTHER_FARM_NO : null);
    }
}
