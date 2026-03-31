package com.schediflow.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubjectRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a hex value in the form #RRGGBB")
        String color,
        @Min(1) @Max(5) Integer difficultyLevel,
        @Size(max = 32) String requiredRoomType,
        @Min(1) Integer maxPerDay,
        @NotBlank String spreadPattern
) {}
