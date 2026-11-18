package com.mnm.api.farmer;

import com.mnm.api.domain.symptom.SymptomReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerSymptomReportRepository extends JpaRepository<SymptomReport, Long> {

    @EntityGraph(attributePaths = "cow")
    Optional<SymptomReport> findByIdAndFarmId(Long id, Long farmId);
}
