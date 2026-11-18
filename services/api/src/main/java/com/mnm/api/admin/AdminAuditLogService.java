package com.mnm.api.admin;

import com.mnm.api.domain.audit.AuditLog;
import com.mnm.api.domain.user.User;
import com.mnm.api.global.response.PageResponse;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AdminAuditLogService {

    /** to 를 안 보냈을 때의 끝 (PostgreSQL timestamptz 범위 안) */
    private static final Instant NO_END = Instant.parse("9999-12-31T00:00:00Z");

    private final AdminAuditRepository auditRepository;

    /** A-S08 표 한 줄. 수정할 수 없는 기록이라 조회만 있다 */
    record AuditLogRow(Long auditLogId, Instant at, Long usersId, String userName, String userRole, String action,
            String targetType, Long targetId) {
    }

    /** ADM-12 */
    @Transactional(readOnly = true)
    PageResponse<AuditLogRow> list(Long userId, String action, String targetType, Instant from, Instant to,
            String cursor) {
        List<AuditLog> rows = auditRepository.search(userId, action, targetType, from == null ? Instant.EPOCH : from,
                to == null ? NO_END : to, AdminSupport.cursor(cursor), Limit.of(AdminSupport.PAGE_SIZE + 1));
        return AdminSupport.page(rows, AdminSupport.PAGE_SIZE, AdminAuditLogService::toRow, AuditLog::getId);
    }

    private static AuditLogRow toRow(AuditLog a) {
        User u = a.getUser();
        return new AuditLogRow(a.getId(), a.getAt(), u.getId(), u.getName(), u.getRole(), a.getAction(),
                a.getTargetType(), a.getTargetId());
    }
}
