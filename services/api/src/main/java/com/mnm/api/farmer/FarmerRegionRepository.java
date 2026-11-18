package com.mnm.api.farmer;

import com.mnm.api.domain.publicdata.RegionCodeMap;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerRegionRepository extends JpaRepository<RegionCodeMap, String> {
}
