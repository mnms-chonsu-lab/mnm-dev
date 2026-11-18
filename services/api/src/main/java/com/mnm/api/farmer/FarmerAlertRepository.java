package com.mnm.api.farmer;

import com.mnm.api.domain.alert.Alert;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerAlertRepository extends JpaRepository<Alert, Long> {

    long countByUserIdAndReadAtIsNull(Long userId);

    List<Alert> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long cursor, Pageable pageable);

    Optional<Alert> findByIdAndUserId(Long id, Long userId);
}
