package com.mnm.api.domain.user;

import com.mnm.api.domain.common.BaseTimeEntity;
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
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 로그인 계정 (농가 · 접수자 · 운영자). name, phone은 개인정보. */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

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

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** 연속으로 틀리면 잠그는 횟수와 잠금 시간 (기능명세서 AUTH-LOCK) */
    public static final int MAX_FAILED_COUNT = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean isActive() {
        return "active".equals(status);
    }

    /** 농가 계정 (A-P01). 비밀번호 없이 농장식별번호 + 휴대폰 번호로 로그인한다 */
    public static User farmer(String name, String phone) {
        User user = new User();
        user.role = "farmer";
        user.name = name;
        user.phone = phone;
        return user;
    }

    /** 접수자·운영자 계정 (A-P01). 임시 비밀번호(해시)라 첫 로그인 때 바꿔야 한다 (R-S06) */
    public static User staff(String role, String name, String loginId, String phone, ReceiverOrg org, String inboxId,
            String tempPasswordHash) {
        User user = new User();
        user.role = role;
        user.name = name;
        user.loginId = loginId;
        user.phone = phone;
        user.org = org;
        user.inboxId = inboxId;
        user.passwordHash = tempPasswordHash;
        user.mustChangePw = true;
        return user;
    }

    /** 소속 기관·수신함 변경 (A-S03) */
    public void assignOrg(ReceiverOrg org, String inboxId) {
        this.org = org;
        this.inboxId = inboxId;
    }

    /** active | suspended (A-S03 · A-S03F). 정지하면 로그인할 수 없다 */
    public void changeStatus(String status) {
        this.status = status;
    }

    /** 비밀번호 초기화 (A-S03): 새 임시 비밀번호(해시), 잠금 해제, 다음 로그인 때 R-S06 */
    public void resetPassword(String tempPasswordHash) {
        passwordHash = tempPasswordHash;
        mustChangePw = true;
        failedCount = 0;
        lockedUntil = null;
    }

    /** 틀린 횟수를 올리고, 5번째면 10분 잠근다. 잠그면 횟수는 0부터 다시 센다 */
    public void recordLoginFailure(Instant now) {
        failedCount++;
        if (failedCount >= MAX_FAILED_COUNT) {
            failedCount = 0;
            lockedUntil = now.plus(LOCK_DURATION);
        }
    }

    public void recordLoginSuccess(Instant now) {
        failedCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /** 임시 비밀번호를 새 비밀번호로 바꾼다 (R-S06). 인자는 해시값 */
    public void changePassword(String newPasswordHash) {
        passwordHash = newPasswordHash;
        mustChangePw = false;
    }

    /** 농가 연락처 변경 (F-P06). 농가는 이 번호로 다음에 로그인한다 */
    public void changePhone(String phone) {
        this.phone = phone;
    }
}
