package com.mnm.api.farmer;

import com.mnm.api.domain.delivery.Delivery;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FarmerDeliveryRepository extends JpaRepository<Delivery, Long> {

    /** 이 농장이 보낸 전달 (소 상태 기록·이력 신고서), cursor 보다 작은 번호부터 최근순 */
    @EntityGraph(attributePaths = {"org", "ccOrg", "historyReport", "reportSummary.symptomReport.cow"})
    @Query("""
            select d from Delivery d
            left join d.reportSummary s left join s.symptomReport sr left join d.historyReport h
            where (sr.farm.id = :farmId or h.farm.id = :farmId) and d.id < :cursor
            order by d.id desc
            """)
    List<Delivery> findMine(@Param("farmId") Long farmId, @Param("cursor") Long cursor, Pageable pageable);

    @Query("""
            select count(d) from Delivery d
            left join d.reportSummary s left join s.symptomReport sr left join d.historyReport h
            where sr.farm.id = :farmId or h.farm.id = :farmId
            """)
    long countMine(@Param("farmId") Long farmId);

    /** 소 상태 기록 전달 중 승인 시각이 [from, to) 인 것 (일지 기록이 함께 보내진 전달) */
    @EntityGraph(attributePaths = {"org", "ccOrg"})
    @Query("""
            select d from Delivery d join d.reportSummary s join s.symptomReport sr
            where sr.farm.id = :farmId and d.approvedAt >= :from and d.approvedAt < :to
            order by d.id desc
            """)
    List<Delivery> findSymptomDeliveries(@Param("farmId") Long farmId, @Param("from") Instant from,
            @Param("to") Instant to);

    boolean existsByReportSummarySymptomReportId(Long symptomReportId);
}
