package com.mnm.api.receiver;

import com.mnm.api.domain.diary.DiaryEntry;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReceiverDiaryRepository extends JpaRepository<DiaryEntry, Long> {

    /** 최근 14일 일지 */
    @EntityGraph(attributePaths = "cow")
    List<DiaryEntry> findByFarmIdAndDateGreaterThanEqualOrderByDateDescIdDesc(Long farmId, LocalDate since);

    /** 분류별 가장 최근 기록 (농장 기본 정보: 접종 · 소독 · 출입) */
    @EntityGraph(attributePaths = "cow")
    Optional<DiaryEntry> findFirstByFarmIdAndCategoryOrderByDateDescIdDesc(Long farmId, String category);

    /** 농가별 마지막 기록 날짜 [farm_id, date] */
    @Query("select d.farm.id, max(d.date) from DiaryEntry d where d.farm.id in :farmIds group by d.farm.id")
    List<Object[]> findLastDates(@Param("farmIds") Collection<Long> farmIds);
}
