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

    public static Task create(FarmInfo farm, String kind, String title, LocalDate dueDate, Long refId) {
        Task task = new Task();
        task.farm = farm;
        task.kind = kind;
        task.title = title;
        task.dueDate = dueDate;
        task.refId = refId;
        return task;
    }

    public void complete(Instant now) {
        if (doneAt == null) {
            doneAt = now;
        }
    }

    /** 신고서 발생일이 바뀌면 기한도 다시 계산한 값으로 바꾼다 */
    public void reschedule(LocalDate dueDate) {
        this.dueDate = dueDate;
    }
}
