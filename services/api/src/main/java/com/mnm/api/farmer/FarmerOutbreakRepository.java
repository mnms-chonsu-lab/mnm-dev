package com.mnm.api.farmer;

import com.mnm.api.domain.publicdata.OutbreakInfo;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FarmerOutbreakRepository extends JpaRepository<OutbreakInfo, Long> {

    /** 시군구(법정동 앞 5자리) 안에서 since 이후 발생한 질병명 접두어별 건수 */
    @Query("""
            select count(o) from OutbreakInfo o
            where o.regionCode like concat(:sigungu, '%') and o.diseaseName like concat(:disease, '%')
              and o.occurredOn >= :since
            """)
    long countRecent(@Param("sigungu") String sigungu, @Param("disease") String disease,
            @Param("since") LocalDate since);

    /** 수집 기준 시각 (화면 "{기준 날짜} 기준") */
    @Query("select max(o.collectedAt) from OutbreakInfo o")
    Instant lastCollectedAt();
}
