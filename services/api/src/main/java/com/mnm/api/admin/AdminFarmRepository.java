package com.mnm.api.admin;

import com.mnm.api.domain.farm.FarmInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AdminFarmRepository extends JpaRepository<FarmInfo, Long> {

    boolean existsByFarmNo(String farmNo);

    Optional<FarmInfo> findFirstByOwnerId(Long ownerId);
}
