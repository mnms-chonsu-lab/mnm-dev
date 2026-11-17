package com.mnm.api.domain.task;

import com.mnm.api.domain.farm.FarmInfo;
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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 기한이 있는 할 일. */
@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tasks_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    private FarmInfo farm;

    /** birth_report | ear_tag 등 */
    @Column(nullable = false, length = 30)
    private String kind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** 참조 번호 (일지 · 이력 신고서, FK 없음) */
    @Column(name = "ref_id")
    private Long refId;

    /** null = 미완료 */
    @Column(name = "done_at")
    private Instant doneAt;
}
