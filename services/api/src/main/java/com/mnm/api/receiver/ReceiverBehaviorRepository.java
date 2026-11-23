package com.mnm.api.receiver;

import com.mnm.api.domain.symptom.BehaviorRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReceiverBehaviorRepository extends JpaRepository<BehaviorRecord, Long> {

    List<BehaviorRecord> findByMediaId(Long mediaId);
}
