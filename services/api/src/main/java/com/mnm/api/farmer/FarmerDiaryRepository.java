package com.mnm.api.farmer;

import com.mnm.api.domain.diary.DiaryEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FarmerDiaryRepository extends JpaRepository<DiaryEntry, Long> {

    @EntityGraph(attributePaths = "cow")
    List<DiaryEntry> findByFarmIdAndDateBetweenOrderByDateDescIdDesc(Long farmId, LocalDate from, LocalDate to);

    Optional<DiaryEntry> findFirstByFarmIdAndDateLessThanEqualOrderByDateDesc(Long farmId, LocalDate date);

    @EntityGraph(attributePaths = "cow")
    Optional<DiaryEntry> findByIdAndFarmId(Long id, Long farmId);

    /** 소마다 가장 최근 일지 날짜 [cow_id, date] (소 목록 정렬용) */
    @Query("select d.cow.id, max(d.date) from DiaryEntry d where d.farm.id = :farmId and d.cow is not null group by d.cow.id")
    List<Object[]> latestDateByCow(@Param("farmId") Long farmId);
}
