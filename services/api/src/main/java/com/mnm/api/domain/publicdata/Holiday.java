package com.mnm.api.domain.publicdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공휴일 (신고 기한 계산용). */
@Entity
@Table(name = "holidays")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holiday {

    @Id
    @Column(name = "date")
    private LocalDate date;

    @Column(nullable = false, length = 50)
    private String name;
}
