package com.mnm.api.domain.delivery;

import com.mnm.api.domain.org.ReceiverOrg;
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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;

/** 접수자 열람 · 처리 기록. 상태가 바뀔 때마다 행을 추가한다. */
@Entity
@Table(name = "receiver_check")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiverCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "receiver_check_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deliveries_id", nullable = false)
    private Delivery delivery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id")
    private ReceiverOrg org;

    /** 처리한 접수자 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", nullable = false)
    private User user;

    /** new | checking | done */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 30)
    private String result;

    @Column(columnDefinition = "text")
    private String memo;

    @Generated
    @Column(nullable = false)
    private Instant at;

    /** 상태 변경 · 메모 1건. 시각(at)은 DB 기본값으로 채운다 */
    public static ReceiverCheck record(Delivery delivery, ReceiverOrg org, User user, String status, String result,
            String memo) {
        ReceiverCheck check = new ReceiverCheck();
        check.delivery = delivery;
        check.org = org;
        check.user = user;
        check.status = status;
        check.result = result;
        check.memo = memo;
        return check;
    }
}
