package com.mnm.api.receiver;

import com.mnm.api.domain.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReceiverUserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "org")
    Optional<User> findWithOrgById(Long id);
}
