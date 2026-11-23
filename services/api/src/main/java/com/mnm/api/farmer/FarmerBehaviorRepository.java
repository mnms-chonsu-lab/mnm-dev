package com.mnm.api.farmer;

import com.mnm.api.domain.symptom.BehaviorRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerBehaviorRepository extends JpaRepository<BehaviorRecord, Long> {

    List<BehaviorRecord> findByMediaId(Long mediaId);
}
