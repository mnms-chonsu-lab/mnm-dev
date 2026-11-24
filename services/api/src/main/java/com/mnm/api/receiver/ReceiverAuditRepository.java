package com.mnm.api.receiver;

import com.mnm.api.domain.audit.AuditLog;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReceiverAuditRepository extends JpaRepository<AuditLog, Long> {

    boolean existsByUserIdAndActionAndTargetTypeAndTargetIdAndAtAfter(Long userId, String action, String targetType,
            Long targetId, Instant after);
}
