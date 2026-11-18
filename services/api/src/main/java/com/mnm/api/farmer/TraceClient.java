package com.mnm.api.farmer;

import java.time.LocalDate;

/**
 * 귀표 12자리 → 축산물이력제 조회 (REQ-DATA-11, 인교님 조회 모듈). 값은 가공하지 않고 그대로 쓴다.
 * 없으면 NOT_FOUND, 조회 실패면 FAILED 를 돌려준다 (예외로 흐름을 멈추지 않는다).
 */
interface TraceClient {

    TraceResult lookup(String tagNo);

    enum Status { FOUND, NOT_FOUND, FAILED }

    /** farmNo: 이력제에 등록된 농장식별번호 (모르면 null) */
    record TraceResult(Status status, String breed, String sex, LocalDate birthDate, LocalDate vaccineLastDate,
            Integer vaccineLastOrder, String farmNo) {

        static TraceResult of(Status status) {
            return new TraceResult(status, null, null, null, null, null, null);
        }
    }
}
