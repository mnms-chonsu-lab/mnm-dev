package com.mnm.api.receiver;

import com.mnm.api.domain.farm.FarmInfo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReceiverFarmRepository extends JpaRepository<FarmInfo, Long> {

    /** 관할 농가 = 우리 기관이 담당 축협인 농가 + 농장 시군구(법정동 앞 5자리)가 관할 지역인 농가 */
    @EntityGraph(attributePaths = "owner")
    @Query("""
            select f from FarmInfo f
            where f.org.id = :orgId or substring(f.regionCode, 1, 5) in :sigungu
            order by f.id
            """)
    List<FarmInfo> findJurisdiction(@Param("orgId") Long orgId, @Param("sigungu") Collection<String> sigungu);

    @EntityGraph(attributePaths = "owner")
    Optional<FarmInfo> findWithOwnerById(Long id);
}
