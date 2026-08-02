package com.schediflow.api.v1;

import com.schediflow.dto.request.TemporaryScheduleRequest;
import com.schediflow.dto.response.TemporaryScheduleResponse;
import com.schediflow.service.TemporaryScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Temporary schedules — date-bounded overlays on a base timetable.
 * Writes require ADMIN or MOD; reads are open to all authenticated users.
 */
@RestController
@RequestMapping("/api/v1/temporary-schedules")
public class TemporaryScheduleController {

    private final TemporaryScheduleService temporaryScheduleService;

    public TemporaryScheduleController(TemporaryScheduleService temporaryScheduleService) {
        this.temporaryScheduleService = temporaryScheduleService;
    }

    /** @return 200 with all temporary schedules for the tenant, earliest first */
    @GetMapping
    public ResponseEntity<List<TemporaryScheduleResponse>> list() {
        return ResponseEntity.ok(temporaryScheduleService.list());
    }

    /** @return 200 if found; 404 if not found or in another tenant */
    @GetMapping("/{id}")
    public ResponseEntity<TemporaryScheduleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(temporaryScheduleService.getById(id));
    }

    /**
     * Creates an overlay over a base timetable.
     *
     * @return 201 on success; 400 if startDate is not before endDate or the range escapes the term;
     *         404 if the base timetable is not in the tenant;
     *         409 if that timetable already has an active overlay
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<TemporaryScheduleResponse> create(
            @Valid @RequestBody TemporaryScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(temporaryScheduleService.create(request));
    }

    /**
     * Updates an overlay.
     *
     * @return 200 on success; 400 on invalid dates; 404 if the overlay or timetable is missing;
     *         409 if another active overlay already covers the timetable
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<TemporaryScheduleResponse> update(
            @PathVariable Long id, @Valid @RequestBody TemporaryScheduleRequest request) {
        return ResponseEntity.ok(temporaryScheduleService.update(id, request));
    }

    /**
     * Deletes an overlay and its overrides; the base timetable applies again immediately.
     *
     * @return 204 on success; 404 if not found
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        temporaryScheduleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
