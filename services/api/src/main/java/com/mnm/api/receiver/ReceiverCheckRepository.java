package com.mnm.api.receiver;

import com.mnm.api.domain.delivery.ReceiverCheck;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 처리 기록은 상태가 바뀔 때마다 행이 쌓인다. 가장 최근(번호가 큰) 행이 현재 상태 */
interface ReceiverCheckRepository extends JpaRepository<ReceiverCheck, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<ReceiverCheck> findFirstByDeliveryIdOrderByIdDesc(Long deliveryId);

    @Query("""
            select c from ReceiverCheck c where c.id in
              (select max(c2.id) from ReceiverCheck c2 where c2.delivery.id in :deliveryIds group by c2.delivery.id)
            """)
    List<ReceiverCheck> findLatest(@Param("deliveryIds") Collection<Long> deliveryIds);

    /** 우리 기관 수신함의 현재 상태별 건수 [status, count]. 처리 기록이 없는 전달(new)은 빠진다 */
    @Query("""
            select c.status, count(c) from ReceiverCheck c where c.id in
              (select max(c2.id) from ReceiverCheck c2 join c2.delivery d
               where d.status = 'delivered' and (d.org.id = :orgId or d.ccOrg.id = :orgId) group by d.id)
            group by c.status
            """)
    List<Object[]> countLatestByStatus(@Param("orgId") Long orgId);

    /** 처리 이력 타임라인 */
    @EntityGraph(attributePaths = {"user", "org"})
    List<ReceiverCheck> findByDeliveryIdOrderByIdAsc(Long deliveryId);
}
