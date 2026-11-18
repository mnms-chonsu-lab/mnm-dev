package com.mnm.api.farmer;

import com.mnm.api.domain.report.ReportSummary;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerReportSummaryRepository extends JpaRepository<ReportSummary, Long> {
}
