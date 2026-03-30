package com.schediflow.api.v1;

import com.schediflow.dto.request.HolidayDateRequest;
import com.schediflow.dto.request.HolidayDateUpdateRequest;
import com.schediflow.dto.response.HolidayDateResponse;
import com.schediflow.service.HolidayDateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/holidays/{calendarId}/dates")
@PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
public class HolidayDateController {

    private final HolidayDateService holidayDateService;

    public HolidayDateController(HolidayDateService holidayDateService) {
        this.holidayDateService = holidayDateService;
    }

    /**
     * Adds a single holiday date to the given calendar.
     *
     * @return 201 on success; 400 if date is duplicate within the calendar or validation fails;
     *         404 if calendarId not found or belongs to a different tenant
     */
    @PostMapping
    public ResponseEntity<HolidayDateResponse> addDate(
            @PathVariable Long calendarId,
            @Valid @RequestBody HolidayDateRequest request) {
        HolidayDateResponse body = holidayDateService.addDate(calendarId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /**
     * Updates the name and/or type of an existing holiday date.
     *
     * @return 200 on success; 400 on validation failure; 404 if calendarId or dateId not found in tenant
     */
    @PutMapping("/{dateId}")
    public ResponseEntity<HolidayDateResponse> updateDate(
            @PathVariable Long calendarId,
            @PathVariable Long dateId,
            @Valid @RequestBody HolidayDateUpdateRequest request) {
        return ResponseEntity.ok(holidayDateService.updateDate(calendarId, dateId, request));
    }

    /**
     * Removes a holiday date from the calendar.
     *
     * @return 204 on success; 404 if calendarId or dateId not found in tenant
     */
    @DeleteMapping("/{dateId}")
    public ResponseEntity<Void> deleteDate(
            @PathVariable Long calendarId,
            @PathVariable Long dateId) {
        holidayDateService.deleteDate(calendarId, dateId);
        return ResponseEntity.noContent().build();
    }
}
