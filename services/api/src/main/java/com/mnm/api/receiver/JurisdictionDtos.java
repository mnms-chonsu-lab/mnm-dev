package com.mnm.api.receiver;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

final class JurisdictionDtos {

    private JurisdictionDtos() {
    }

    /** RCV-06. farms 는 연락 우선순위(계산식 ⑩) 순서 */
    record JurisdictionResponse(Kpis kpis, List<FarmRow> farms) {
    }

    /** 기한 7일 이내 · 알림 48시간 미확인 · 50두 미만 · 오늘 기록 남긴 농가 수 */
    record Kpis(long dueSoon, long unanswered, long smallFarms, long recordedToday) {
    }

    /**
     * alert_state: unanswered(48시간 넘게 안 읽음 → [다시 알리기]) | waiting(보낸 지 48시간 안 됨) | seen(확인함) | null(알림 없음).
     * phone 은 우리 기관에 보낸 가장 최근 기록에서 연락처를 함께 보낸 경우만 (아니면 null). reasons: unanswered | due_soon | small
     */
    record FarmRow(Long farmId, String name, String address, String regionCode, Integer headCount, String phoneMasked,
            boolean phoneShared, String phone, LocalDate lastRecordDate, boolean recordedToday, NextTask nextTask,
            String alertState, List<String> reasons) {
    }

    /** 가장 가까운 미완료 할 일 (기한 지난 것 포함). d_day 음수 = 기한 지남 */
    record NextTask(String kind, String title, LocalDate dueDate, long dDay) {
    }

    record NudgeResponse(Long alertId, Instant createdAt) {
    }
}
