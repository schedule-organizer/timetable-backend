package com.schediflow.repository;

import com.schediflow.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>,
        JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndTenantId(Long id, Long tenantId);

    /** Recipients for tenant-wide notifications (NOTIF-02 / NOTIF-03). */
    java.util.List<User> findByTenantIdAndStatus(Long tenantId, String status);
}
