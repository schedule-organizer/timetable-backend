package com.schediflow.repository;

import com.schediflow.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Room> findByIdAndTenantIdAndActive(Long id, Long tenantId, boolean active);

    List<Room> findByTenantIdAndActiveOrderByNameAsc(Long tenantId, boolean active);

    Optional<Room> findByNameAndTenantIdAndActive(String name, Long tenantId, boolean active);

    boolean existsByNameAndTenantIdAndActive(String name, Long tenantId, boolean active);

    boolean existsByNameAndTenantIdAndActiveAndIdNot(String name, Long tenantId, boolean active, Long id);
}
