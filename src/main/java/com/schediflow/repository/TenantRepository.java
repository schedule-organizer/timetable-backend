package com.schediflow.repository;

import com.schediflow.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    boolean existsBySlug(String slug);

    Optional<Tenant> findBySlug(String slug);
}
