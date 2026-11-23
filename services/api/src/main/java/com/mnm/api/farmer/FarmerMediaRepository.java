package com.mnm.api.farmer;

import com.mnm.api.domain.symptom.FieldMedia;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerMediaRepository extends JpaRepository<FieldMedia, Long> {

    List<FieldMedia> findBySymptomReportIdAndKindOrderByIdAsc(Long symptomReportId, String kind);

    /** 내 농장 소 상태 기록에 딸린 사진·영상 */
    Optional<FieldMedia> findByIdAndSymptomReportFarmId(Long id, Long farmId);
}
