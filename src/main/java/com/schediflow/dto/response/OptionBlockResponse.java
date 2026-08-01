package com.schediflow.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record OptionBlockResponse(
        Long id,
        String name,
        String description,
        List<Long> memberGroupIds,
        boolean active,
        OffsetDateTime createdAt
) {}
