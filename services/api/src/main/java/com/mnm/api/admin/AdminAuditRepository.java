package com.mnm.api.admin;

import com.mnm.api.domain.audit.AuditLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AdminAuditRepository extends JpaRepository<AuditLog, Long> {

    /**
     * A-S08 감사 로그. 최근순, 기간은 [from, to).
     * from · to 는 null 이면 안 된다: PostgreSQL 이 null 시각 매개변수의 타입을 정하지 못해 쿼리가 실패한다
     */
    @Query("""
            select a from AuditLog a join fetch a.user u
            where (:userId is null or u.id = :userId)
              and (:action is null or a.action = :action)
              and (:targetType is null or a.targetType = :targetType)
              and a.at >= :from and a.at < :to
              and a.id < :cursor
            order by a.id desc
            """)
    List<AuditLog> search(@Param("userId") Long userId, @Param("action") String action,
            @Param("targetType") String targetType, @Param("from") Instant from, @Param("to") Instant to,
            @Param("cursor") Long cursor, Limit limit);
}
