package com.mnm.api.auth;

import com.mnm.api.domain.farm.FarmInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuthFarmRepository extends JpaRepository<FarmInfo, Long> {

    @EntityGraph(attributePaths = "owner")
    Optional<FarmInfo> findByFarmNo(String farmNo);

    @EntityGraph(attributePaths = "org")
    Optional<FarmInfo> findFirstByOwnerId(Long ownerUserId);
}
