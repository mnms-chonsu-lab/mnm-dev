package com.mnm.api.domain.diary;

import com.mnm.api.domain.farm.Cow;
import com.mnm.api.domain.farm.FarmInfo;
import com.mnm.api.domain.symptom.FieldMedia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 날짜별 구조화 일지. */
@Entity
@Table(name = "diary_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    private FarmInfo farm;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    /** 사육현황 | 접종 | 소독 | 관찰 | 출입 | 출생 | 폐사 | 이동 | 메모 + 신고(신고·전달 시 서버가 기록) */
    @Column(nullable = false, length = 20)
    private String category;

    /** null = 농장 전체 기록 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cow_id")
    private Cow cow;

    @Column(name = "value", length = 500)
    private String value;

    @Column(name = "vaccine_type", length = 50)
    private String vaccineType;

    @Column(name = "head_count")
    private Integer headCount;

    /** 음성 인식 원문 */
    @Column(name = "raw_text", columnDefinition = "text")
    private String rawText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audio_media_id")
    private FieldMedia audioMedia;

    /** 확인된 일지 항목 저장 (FARM-04). 음성 원본은 {@link #attachAudio} 로 붙인다 */
    public static DiaryEntry record(FarmInfo farm, LocalDate date, String category, Cow cow, String value,
            String vaccineType, Integer headCount, String rawText) {
        DiaryEntry entry = new DiaryEntry();
        entry.farm = farm;
        entry.rawText = rawText;
        entry.revise(date, category, cow, value, vaccineType, headCount);
        return entry;
    }

    /** 기록 고치기 (FARM-26). 원래 말(raw_text)은 바꾸지 않는다 */
    public void revise(LocalDate date, String category, Cow cow, String value, String vaccineType,
            Integer headCount) {
        this.date = date;
        this.category = category;
        this.cow = cow;
        this.value = value;
        this.vaccineType = vaccineType;
        this.headCount = headCount;
    }

    /** 말로 기록한 음성 원본 (FARM-03 에서 저장한 field_media). null 이면 없음 */
    public void attachAudio(FieldMedia audio) {
        this.audioMedia = audio;
    }
}
