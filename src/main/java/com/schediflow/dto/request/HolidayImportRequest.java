package com.schediflow.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record HolidayImportRequest(
        @NotNull Long calendarId,
        @NotBlank @Size(min = 2, max = 2) @Pattern(regexp = "[A-Za-z]{2}", message = "must be a 2-letter ISO 3166-1 alpha-2 country code") String country,
        @Size(max = 32) String region,
        @NotNull @Min(1900) @Max(2100) Integer year
) {
}
