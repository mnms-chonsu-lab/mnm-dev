package com.mnm.api.domain.user;

import com.mnm.api.domain.org.ReceiverOrg;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;

/** 로그인 계정 (농가 · 접수자 · 운영자). name, phone은 개인정보. */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "users_id")
    private Long id;

    /** farmer | receiver | admin */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 50)
    private String name;

    /** 농가 로그인 키 (농장식별번호와 함께) */
    @Column(length = 20)
    private String phone;

    /** 접수자 · 운영자만 */
    @Column(name = "login_id", unique = true, length = 50)
    private String loginId;

    /** 접수자 · 운영자만. 원문 저장 금지 */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "must_change_pw", nullable = false)
    private boolean mustChangePw;

    /** 접수자만 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id")
    private ReceiverOrg org;

    /** 접수자 소속 수신함 (receiver_org.inboxes의 inbox_id, FK 없음) */
    @Column(name = "inbox_id", length = 50)
    private String inboxId;

    /** active | suspended | invited */
    @Column(nullable = false, length = 20)
    private String status = "active";

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    /** 5회 실패 시 10분 잠금 */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Generated
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
