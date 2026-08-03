package com.schediflow.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record TemplateResponse(
        Long id,
        String name,
        String description,
        String institutionType,
        @JsonProperty("isBuiltIn") boolean isBuiltIn,
        JsonNode configuration,
        OffsetDateTime createdAt
) {}
