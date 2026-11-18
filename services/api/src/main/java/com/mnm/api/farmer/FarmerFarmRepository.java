package com.mnm.api.farmer;

import com.mnm.api.domain.farm.FarmInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerFarmRepository extends JpaRepository<FarmInfo, Long> {

    @EntityGraph(attributePaths = {"owner", "org"})
    Optional<FarmInfo> findFirstByOwnerId(Long ownerUserId);
}
