package com.schediflow.repository;

import com.schediflow.domain.DelegationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DelegationRequestRepository extends JpaRepository<DelegationRequest, Long> {

    Optional<DelegationRequest> findByIdAndTenantId(Long id, Long tenantId);

    List<DelegationRequest> findByTenantIdAndStatusOrderByIdAsc(Long tenantId, String status);
}
