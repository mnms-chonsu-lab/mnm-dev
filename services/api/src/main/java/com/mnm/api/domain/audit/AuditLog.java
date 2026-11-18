package com.mnm.api.domain.audit;

import com.mnm.api.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.net.InetAddress;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 감사 기록 (누가 무엇을 열람 · 변경했는지). */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", nullable = false)
    private User user;

    /** view | update | deliver | approve | promote 등 */
    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Generated
    @Column(nullable = false)
    private Instant at;

    @JdbcTypeCode(SqlTypes.INET)
    private InetAddress ip;

    /** 시각(at)은 DB 기본값으로 채운다 */
    public static AuditLog of(User user, String action, String targetType, Long targetId, InetAddress ip) {
        AuditLog log = new AuditLog();
        log.user = user;
        log.action = action;
        log.targetType = targetType;
        log.targetId = targetId;
        log.ip = ip;
        return log;
    }
}
