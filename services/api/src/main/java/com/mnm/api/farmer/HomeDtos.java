package com.mnm.api.farmer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 홈 요약 (FARM-05), 지역 발생·거점소독시설 (FARM-17) 응답 */
final class HomeDtos {

    private HomeDtos() {
    }

    /** tasks 는 기한 임박순 (끝내지 않은 것만) */
    record HomeResponse(String farmerName, int taskCount, List<TaskResponse> tasks, long unreadAlertCount,
            RegionOutbreak region) {
    }

    /** kind: birth_report | death_report | move_report | ear_tag. ref_id 는 이력 신고서 번호 (FARM-06) */
    record TaskResponse(Long tasksId, String kind, String title, LocalDate dueDate, long dDay, Long refId) {
    }

    /** 농장 시군구 기준 최근 30일 발생 건수. base_at = 공공데이터 수집 기준 시각 (수집 실패 시 직전 값 그대로) */
    record RegionOutbreak(String regionName, long fmdCount, long lsdCount, Instant baseAt) {
    }

    /** 농장 위경도에서 가장 가까운 거점소독시설 (직선거리, km 소수 1자리) */
    record FacilityResponse(String name, String address, double distanceKm, String operatingHours, String phone) {
    }
}
