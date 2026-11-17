package com.mnm.api.domain.alert;

import com.mnm.api.domain.common.BaseTimeEntity;
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

/** 알림 + 농가 피드백. */
@Entity
@Table(name = "alerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alert extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alerts_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", nullable = false)
    private User user;

    /** diff | deadline | result */
    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    /** kind별 대상: diff→cows, deadline→tasks, result→deliveries (FK 없음) */
    @Column(name = "ref_id")
    private Long refId;

    /** seen | ok */
    @Column(length = 10)
    private String feedback;

    @Column(name = "read_at")
    private Instant readAt;
}
