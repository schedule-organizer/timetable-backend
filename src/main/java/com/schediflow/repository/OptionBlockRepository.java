package com.schediflow.repository;

import com.schediflow.domain.OptionBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OptionBlockRepository extends JpaRepository<OptionBlock, Long> {

    Optional<OptionBlock> findByIdAndTenantIdAndActive(Long id, Long tenantId, boolean active);

    List<OptionBlock> findByTenantIdAndActiveOrderByNameAsc(Long tenantId, boolean active);
}
