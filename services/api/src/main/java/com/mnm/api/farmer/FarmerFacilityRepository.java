package com.mnm.api.farmer;

import com.mnm.api.domain.publicdata.DisinfectionFacility;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerFacilityRepository extends JpaRepository<DisinfectionFacility, Long> {

    List<DisinfectionFacility> findByLatIsNotNullAndLngIsNotNull();
}
