package com.mnm.api.receiver;

import com.mnm.api.domain.symptom.FieldMedia;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReceiverMediaRepository extends JpaRepository<FieldMedia, Long> {

    List<FieldMedia> findBySymptomReportIdAndKindOrderByIdAsc(Long symptomReportId, String kind);

    /** 사진이 있는 소 상태 기록 번호 (주간 통계 "사진 포함") */
    @Query("select distinct m.symptomReport.id from FieldMedia m where m.kind = 'photo' and m.symptomReport.id in :ids")
    List<Long> findReportIdsWithPhoto(@Param("ids") Collection<Long> symptomReportIds);
}
