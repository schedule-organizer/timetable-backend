package com.schediflow.api.v1;

import com.schediflow.dto.request.HolidayCalendarRequest;
import com.schediflow.dto.response.HolidayCalendarResponse;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.service.HolidayCalendarService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/holiday-calendars")
public class HolidayCalendarController {

    private final HolidayCalendarService holidayCalendarService;

    public HolidayCalendarController(HolidayCalendarService holidayCalendarService) {
        this.holidayCalendarService = holidayCalendarService;
    }

    /**
     * Returns all holiday calendars for the authenticated tenant.
     *
     * @return 200 with list of calendars
     */
    @GetMapping
    public ResponseEntity<List<HolidayCalendarResponse>> list() {
        return ResponseEntity.ok(holidayCalendarService.list());
    }

    /**
     * Returns a single holiday calendar by id.
     *
     * @return 200 if found; 404 if not found or belongs to a different tenant
     */
    @GetMapping("/{id}")
    public ResponseEntity<HolidayCalendarResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(holidayCalendarService.getById(id));
    }

    /**
     * Creates a new holiday calendar for the tenant.
     *
     * @return 201 on success; 400 on validation failure; 404 if academicYearId not found;
     *         409 if a calendar already exists for the given academic year
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<HolidayCalendarResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody HolidayCalendarRequest request) {
        HolidayCalendarResponse body = holidayCalendarService.create(principal.tenantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /**
     * Updates an existing holiday calendar.
     *
     * @return 200 on success; 400 on validation failure; 404 if calendar or academicYearId not found;
     *         409 if the updated academicYearId is already used by another calendar
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<HolidayCalendarResponse> update(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody HolidayCalendarRequest request) {
        return ResponseEntity.ok(holidayCalendarService.update(principal.tenantId(), id, request));
    }

    /**
     * Deletes a holiday calendar and all its holiday dates (cascade).
     *
     * @return 204 on success; 404 if not found or belongs to a different tenant
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        holidayCalendarService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
