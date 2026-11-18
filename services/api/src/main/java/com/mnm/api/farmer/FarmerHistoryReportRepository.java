package com.mnm.api.farmer;

import com.mnm.api.domain.report.HistoryReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerHistoryReportRepository extends JpaRepository<HistoryReport, Long> {

    Optional<HistoryReport> findByIdAndFarmId(Long id, Long farmId);
}
