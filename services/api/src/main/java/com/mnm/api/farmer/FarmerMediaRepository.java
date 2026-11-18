package com.mnm.api.farmer;

import com.mnm.api.domain.symptom.FieldMedia;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerMediaRepository extends JpaRepository<FieldMedia, Long> {

    List<FieldMedia> findBySymptomReportIdAndKindOrderByIdAsc(Long symptomReportId, String kind);
}
