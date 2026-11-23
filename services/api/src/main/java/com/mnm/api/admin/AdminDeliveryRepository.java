package com.mnm.api.admin;

import com.mnm.api.domain.delivery.Delivery;
import com.mnm.api.domain.delivery.ReceiverCheck;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AdminDeliveryRepository extends JpaRepository<Delivery, Long> {

    /**
     * A-S04 전달 현황. 최근순, 승인 시각 기준 [from, to). 표에 쓰는 농가·기관을 한 번에 읽는다.
     * from · to 는 null 이면 안 된다 (AdminAuditRepository 참고)
     */
    @Query("""
            select d from Delivery d
            left join fetch d.org left join fetch d.ccOrg
            left join fetch d.reportSummary rs left join fetch rs.symptomReport sr
            left join fetch sr.farm sf left join fetch sf.owner
            left join fetch d.historyReport hr left join fetch hr.farm hf left join fetch hf.owner
            where (:route is null or d.route = :route)
              and (:status is null or d.status = :status)
              and (:orgId is null or d.org.id = :orgId or d.ccOrg.id = :orgId)
              and d.approvedAt >= :from and d.approvedAt < :to
              and d.id < :cursor
            order by d.id desc
            """)
    List<Delivery> search(@Param("route") String route, @Param("status") String status, @Param("orgId") Long orgId,
            @Param("from") Instant from, @Param("to") Instant to, @Param("cursor") Long cursor, Limit limit);

    /** 전달별 마지막 처리 기록 (접수자 처리 상태) */
    @Query("""
            select c from ReceiverCheck c
            where c.delivery.id in :ids
              and c.at = (select max(c2.at) from ReceiverCheck c2 where c2.delivery = c.delivery)
            """)
    List<ReceiverCheck> latestChecks(@Param("ids") Collection<Long> ids);
}
