package com.schediflow.dto.response;

import com.schediflow.dto.AvailabilityStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Weekly availability grid for one teacher: every weekday in the scheduling cycle × every period of
 * the default bell schedule, with the effective {@link AvailabilityStatus} of each cell.
 *
 * <p>{@code days} covers recurring forbidden slots and soft preferences. One-off (date-specific)
 * forbidden slots cannot be expressed in a repeating weekly grid and are listed separately in
 * {@code dateSpecificUnavailability}.</p>
 */
public record TeacherAvailabilityResponse(
        Long teacherId,
        List<Long> periodIds,
        List<AvailabilityDayResponse> days,
        List<DateSpecificUnavailabilityResponse> dateSpecificUnavailability
) {

    public record AvailabilityDayResponse(int dayOfWeek, List<AvailabilitySlotResponse> slots) {}

    public record AvailabilitySlotResponse(Long periodId, AvailabilityStatus status) {}

    public record DateSpecificUnavailabilityResponse(LocalDate date, Long periodId) {}
}
