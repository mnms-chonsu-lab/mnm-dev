package com.mnm.api.farmer;

import com.mnm.api.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerUserRepository extends JpaRepository<User, Long> {

    boolean existsByRoleAndPhoneAndIdNot(String role, String phone, Long id);
}
