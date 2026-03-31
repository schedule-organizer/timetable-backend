package com.schediflow.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record RoomResponse(
        Long id,
        String name,
        String type,
        Integer capacity,
        List<String> equipmentTags,
        String building,
        String floor,
        boolean active,
        OffsetDateTime createdAt
) {}
