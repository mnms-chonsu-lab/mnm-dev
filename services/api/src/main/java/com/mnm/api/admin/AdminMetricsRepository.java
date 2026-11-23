package com.mnm.api.admin;

import com.mnm.api.domain.ops.EtlJobRun;
import com.mnm.api.domain.ops.InferenceLog;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 대시보드(A-S01)·농가 상세(A-S03F)의 건수 집계. 내용은 읽지 않고 개수만 센다 */
interface AdminMetricsRepository extends Repository<InferenceLog, Long> {

    @Query(value = "select 1", nativeQuery = true)
    Integer ping();

    /** 일지 기록 수. "신고" 분류는 전달할 때 서버가 남기는 줄이라 뺀다 */
    @Query("""
            select count(d) from DiaryEntry d
            where (:farmId is null or d.farm.id = :farmId) and d.date >= :from and d.category <> '신고'
            """)
    long countDiary(@Param("farmId") Long farmId, @Param("from") LocalDate from);

    /** 소 상태 기록(증상 확인) 수 */
    @Query("""
            select count(s) from SymptomReport s
            where (:farmId is null or s.farm.id = :farmId) and s.createdAt >= :since
            """)
    long countSymptomReports(@Param("farmId") Long farmId, @Param("since") Instant since);

    @Query("select count(d) from Delivery d where d.receivedAt >= :since")
    long countDeliveriesSince(@Param("since") Instant since);

    /** 미처리 접수: 도착한 전달 중 마지막 처리 상태가 "처리 완료"가 아닌 것 (처리 기록 없음 = 새로 도착) */
    @Query("""
            select count(d) from Delivery d
            where d.status = 'delivered'
              and not exists (select 1 from ReceiverCheck c
                              where c.delivery = d and c.status = 'done'
                                and c.at = (select max(c2.at) from ReceiverCheck c2 where c2.delivery = d))
            """)
    long countPendingReceipts();

    /**
     * 계산식 ⑪. 행 = [호출 수, 실패 수, 평균 지연(ms) 또는 null].
     * 실패는 AI 장애(503·504)만. 404 근거 없음·422 입력 문제는 호출 수에만 센다.
     * 형식 오류·토큰 오류는 AI 서버가 기록하지 않고, http_status 가 없는 옛 기록은 뺀다
     */
    @Query("""
            select count(i), coalesce(sum(case when i.httpStatus in (503, 504) then 1 else 0 end), 0), avg(i.latencyMs)
            from InferenceLog i where i.at >= :since and i.httpStatus is not null
            """)
    List<Object[]> inferenceStats(@Param("since") Instant since);

    /** 기능별 계산식 ⑪. 행 = [feature, 호출 수, 실패 수, 평균 지연(ms), 근거 없음(404) 수, 입력 문제(422) 수] */
    @Query("""
            select i.feature, count(i), coalesce(sum(case when i.httpStatus in (503, 504) then 1 else 0 end), 0),
                   avg(i.latencyMs), coalesce(sum(case when i.httpStatus = 404 then 1 else 0 end), 0),
                   coalesce(sum(case when i.httpStatus = 422 then 1 else 0 end), 0)
            from InferenceLog i where i.at >= :since and i.httpStatus is not null
            group by i.feature order by i.feature
            """)
    List<Object[]> inferenceStatsByFeature(@Param("since") Instant since);

    /** A-S07 호출 로그. 최근순, 기간은 [from, to). from · to 는 null 이면 안 된다 (AdminAuditRepository 참고) */
    @Query("""
            select i from InferenceLog i
            where (:feature is null or i.feature = :feature) and i.at >= :from and i.at < :to and i.id < :cursor
            order by i.id desc
            """)
    List<InferenceLog> searchInferenceLogs(@Param("feature") String feature, @Param("from") Instant from,
            @Param("to") Instant to, @Param("cursor") Long cursor, Limit limit);

    /** 평소와 다름 알림 피드백. 행 = [확인함(seen) 수, 문제 없음(ok) 수] */
    @Query("""
            select coalesce(sum(case when a.feedback = 'seen' then 1 else 0 end), 0),
                   coalesce(sum(case when a.feedback = 'ok' then 1 else 0 end), 0)
            from Alert a where a.kind = 'diff'
            """)
    List<Object[]> diffFeedback();

    /** 수집 작업별 마지막 실행 */
    @Query("""
            select e from EtlJobRun e
            where e.startedAt = (select max(e2.startedAt) from EtlJobRun e2 where e2.job = e.job)
            order by e.job
            """)
    List<EtlJobRun> latestEtlRuns();

    @Query("select count(c) from Cow c where c.farm.id = :farmId and c.status = 'active'")
    long countActiveCows(@Param("farmId") Long farmId);

    /** 농가가 보낸 기록 수 (소 상태 기록 · 이력 신고 초안 전달 모두) */
    @Query("""
            select count(d) from Delivery d
            left join d.reportSummary rs left join rs.symptomReport sr
            left join d.historyReport hr
            where sr.farm.id = :farmId or hr.farm.id = :farmId
            """)
    long countFarmDeliveries(@Param("farmId") Long farmId);
}
