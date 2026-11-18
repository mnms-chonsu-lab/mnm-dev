package com.mnm.api.farmer;

import com.mnm.api.domain.publicdata.Holiday;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerHolidayRepository extends JpaRepository<Holiday, LocalDate> {

    List<Holiday> findByDateBetween(LocalDate from, LocalDate to);
}
