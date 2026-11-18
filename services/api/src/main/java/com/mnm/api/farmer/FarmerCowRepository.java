package com.mnm.api.farmer;

import com.mnm.api.domain.farm.Cow;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerCowRepository extends JpaRepository<Cow, Long> {

    List<Cow> findByFarmIdAndStatus(Long farmId, String status);

    /** 목록에서 뺀 소까지 (신고서의 폐사·양도 소, 지난 일지) */
    List<Cow> findByFarmId(Long farmId);

    Optional<Cow> findByIdAndFarmId(Long id, Long farmId);

    Optional<Cow> findByFarmIdAndTagFull(Long farmId, String tagFull);

    long countByFarmIdAndStatus(Long farmId, String status);
}
