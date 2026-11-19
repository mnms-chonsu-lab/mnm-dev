package com.mnm.api.receiver;

import com.mnm.api.domain.farm.Cow;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReceiverCowRepository extends JpaRepository<Cow, Long> {

    Optional<Cow> findFirstByFarmIdAndTagLast4AndStatus(Long farmId, String tagLast4, String status);
}
