package com.mnm.api.auth;

import com.mnm.api.domain.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuthUserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);
}
