package com.schediflow.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RoomRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank String type,
        @Min(1) Integer capacity,
        List<String> equipmentTags,
        @Size(max = 200) String building,
        @Size(max = 100) String floor
) {}
