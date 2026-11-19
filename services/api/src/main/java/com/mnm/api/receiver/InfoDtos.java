package com.mnm.api.receiver;

import java.time.LocalDate;
import java.util.List;

final class InfoDtos {

    private InfoDtos() {
    }

    /** RCV-08 공식 지침 1건 (문서별 아코디언). 어드민에서 게시한 요약(kind=summary)만, revised_at = 가장 최근 개정일 */
    record GuideDoc(String sourceDoc, String disease, LocalDate revisedAt, List<GuideItem> items) {
    }

    record GuideItem(Long ruleId, String text, String clause) {
    }

    /** RCV-09 최근 7일 (오늘 포함, 한국 시간). done·with_photo 는 이 기간에 도착한 기록 중 */
    record WeeklyStats(LocalDate from, LocalDate to, long received, long done, long withPhoto,
            List<DailyCount> daily) {
    }

    record DailyCount(LocalDate date, long count) {
    }
}
