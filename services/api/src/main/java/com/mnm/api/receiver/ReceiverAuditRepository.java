package com.mnm.api.receiver;

import com.mnm.api.domain.audit.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReceiverAuditRepository extends JpaRepository<AuditLog, Long> {
}
