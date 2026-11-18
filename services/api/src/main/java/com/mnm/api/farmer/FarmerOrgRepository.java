package com.mnm.api.farmer;

import com.mnm.api.domain.org.ReceiverOrg;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerOrgRepository extends JpaRepository<ReceiverOrg, Long> {

    List<ReceiverOrg> findByType(String type);
}
