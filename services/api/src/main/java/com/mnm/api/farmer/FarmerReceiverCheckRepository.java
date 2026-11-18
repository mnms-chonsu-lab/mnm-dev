package com.mnm.api.farmer;

import com.mnm.api.domain.delivery.ReceiverCheck;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerReceiverCheckRepository extends JpaRepository<ReceiverCheck, Long> {

    /** 접수자 처리 상태 (변경마다 행이 쌓이므로 가장 최근 것) */
    Optional<ReceiverCheck> findFirstByDeliveryIdOrderByAtDescIdDesc(Long deliveryId);
}
