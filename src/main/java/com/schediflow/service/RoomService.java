package com.schediflow.service;

import com.schediflow.domain.Room;
import com.schediflow.domain.RoomType;
import com.schediflow.dto.request.RoomRequest;
import com.schediflow.dto.response.RoomResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.RoomRepository;
import com.schediflow.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public List<RoomResponse> list() {
        Long tenantId = TenantContext.getTenantId();
        return roomRepository.findByTenantIdAndActiveOrderByNameAsc(tenantId, true)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public RoomResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public RoomResponse create(RoomRequest req) {
        Long tenantId = TenantContext.getTenantId();
        String normalizedType = validateAndNormalizeType(req.type());
        assertNameAvailable(tenantId, req.name(), null);

        Room room = new Room();
        room.setTenantId(tenantId);
        mapFields(room, req, normalizedType);
        return toResponse(roomRepository.save(room));
    }

    @Transactional
    public RoomResponse update(Long id, RoomRequest req) {
        Long tenantId = TenantContext.getTenantId();
        Room room = findOrThrow(id);
        String normalizedType = validateAndNormalizeType(req.type());
        assertNameAvailable(tenantId, req.name(), id);

        mapFields(room, req, normalizedType);
        return toResponse(roomRepository.save(room));
    }

    @Transactional
    public void delete(Long id) {
        Room room = findOrThrow(id);
        room.setActive(false);
        roomRepository.save(room);
    }

    // D3: only active rooms are accessible — soft-deleted rooms return 404
    private Room findOrThrow(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return roomRepository.findByIdAndTenantIdAndActive(id, tenantId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + id));
    }

    // P3: normalizes type to uppercase; P5: derives valid names from enum
    private String validateAndNormalizeType(String type) {
        String normalized = type.toUpperCase();
        boolean valid = Arrays.stream(RoomType.values())
                .anyMatch(rt -> rt.name().equals(normalized));
        if (!valid) {
            String validTypes = Arrays.stream(RoomType.values())
                    .map(RoomType::name)
                    .collect(Collectors.joining(", "));
            throw new BadRequestException("Invalid room type: " + type + ". Must be one of: " + validTypes);
        }
        return normalized;
    }

    private void assertNameAvailable(Long tenantId, String name, Long excludeId) {
        boolean taken = excludeId == null
                ? roomRepository.existsByNameAndTenantIdAndActive(name, tenantId, true)
                : roomRepository.existsByNameAndTenantIdAndActiveAndIdNot(name, tenantId, true, excludeId);
        if (taken) {
            throw new ConflictException("Room name already exists: " + name);
        }
    }

    private void mapFields(Room room, RoomRequest req, String normalizedType) {
        room.setName(req.name());
        room.setType(normalizedType);
        room.setCapacity(req.capacity());
        room.setEquipmentTags(req.equipmentTags() != null ? req.equipmentTags() : List.of());
        room.setBuilding(req.building());
        room.setFloor(req.floor());
    }

    private RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getType(),
                room.getCapacity(),
                room.getEquipmentTags() != null ? room.getEquipmentTags() : List.of(),
                room.getBuilding(),
                room.getFloor(),
                room.isActive(),
                room.getCreatedAt());
    }
}
