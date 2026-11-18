package com.mnm.api.admin;

import com.mnm.api.domain.org.ReceiverOrg;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AdminOrgRepository extends JpaRepository<ReceiverOrg, Long> {

    List<ReceiverOrg> findAllByOrderByIdAsc();
}
