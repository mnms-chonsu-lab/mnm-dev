package com.mnm.api.receiver;

import com.mnm.api.domain.delivery.Delivery;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 접수자 수신함 = 우리 기관이 받는 기관(org)이거나 함께 받는 축협(cc_org)인 전달 중 도착(delivered)한 것 */
interface ReceiverDeliveryRepository extends JpaRepository<Delivery, Long> {

    /**
     * cursor 보다 작은 번호부터 최근순. status 는 new | checking | done | all.
     * 처리 기록이 없으면 new (상태가 바뀔 때마다 receiver_check 행이 쌓이므로 가장 최근 행이 현재 상태)
     */
    @EntityGraph(attributePaths = {"historyReport.farm", "reportSummary.symptomReport.farm",
            "reportSummary.symptomReport.cow"})
    @Query("""
            select d from Delivery d
            where d.status = 'delivered' and (d.org.id = :orgId or d.ccOrg.id = :orgId) and d.id < :cursor
              and (:status = 'all' or coalesce((select c.status from ReceiverCheck c where c.id =
                    (select max(c2.id) from ReceiverCheck c2 where c2.delivery = d)), 'new') = :status)
            order by d.id desc
            """)
    List<Delivery> findInbox(@Param("orgId") Long orgId, @Param("status") String status,
            @Param("cursor") Long cursor, Pageable pageable);

    @Query("""
            select count(d) from Delivery d
            where d.status = 'delivered' and (d.org.id = :orgId or d.ccOrg.id = :orgId)
            """)
    long countInbox(@Param("orgId") Long orgId);

    /** 상태 변경·메모는 같은 전달을 동시에 고치지 않도록 행을 잠근다 (먼저 처리한 담당자 확인 → CONFLICT-409) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> findForUpdate(@Param("id") Long id);

    Optional<Delivery> findFirstByHistoryReportIdOrderByIdDesc(Long historyReportId);

    /** 주간 통계: 도착 시각이 since 이후인 우리 기관 전달 */
    @EntityGraph(attributePaths = "reportSummary")
    @Query("""
            select d from Delivery d
            where d.status = 'delivered' and (d.org.id = :orgId or d.ccOrg.id = :orgId) and d.receivedAt >= :since
            """)
    List<Delivery> findReceivedSince(@Param("orgId") Long orgId, @Param("since") Instant since);

    /** 관할 농가별 우리 기관이 받은 전달 [farm_id, share_phone, deliveries_id] (연락처 공개 여부) */
    @Query("""
            select coalesce(sr.farm.id, h.farm.id), d.sharePhone, d.id from Delivery d
            left join d.reportSummary s left join s.symptomReport sr left join d.historyReport h
            where (d.org.id = :orgId or d.ccOrg.id = :orgId) and (sr.farm.id in :farmIds or h.farm.id in :farmIds)
            """)
    List<Object[]> findSharePhones(@Param("orgId") Long orgId, @Param("farmIds") Collection<Long> farmIds);
}
