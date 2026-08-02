package com.schediflow.api.v1;

import com.schediflow.dto.request.TimetableRequest;
import com.schediflow.dto.request.TimetableStatusRequest;
import com.schediflow.dto.response.TimetableLessonResponse;
import com.schediflow.dto.response.TimetableResponse;
import com.schediflow.service.TimetableGridService;
import com.schediflow.service.TimetableService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Timetables and their lifecycle. Writes require ADMIN or MOD; reads are open to all authenticated
 * users, since every role needs to see the schedule.
 */
@RestController
@RequestMapping("/api/v1/timetables")
public class TimetableController {

    private final TimetableService timetableService;
    private final TimetableGridService timetableGridService;

    public TimetableController(
            TimetableService timetableService, TimetableGridService timetableGridService) {
        this.timetableService = timetableService;
        this.timetableGridService = timetableGridService;
    }

    /**
     * Every lesson in the timetable, ready for grid rendering. Readable by all authenticated roles.
     *
     * <p>Filters are optional and combine. {@code teacherId} is a {@code teachers.id}, matching the
     * rest of the API.</p>
     *
     * @return 200 with the lessons; 404 if the timetable is not in the tenant
     */
    @GetMapping("/{id}/lessons")
    public ResponseEntity<List<TimetableLessonResponse>> lessons(
            @PathVariable Long id,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long roomId) {
        return ResponseEntity.ok(timetableGridService.getLessons(id, teacherId, classId, roomId));
    }

    /**
     * Lists timetables, optionally narrowed by term and/or status.
     *
     * @return 200 with matching timetables; 400 if {@code status} is not a known state
     */
    @GetMapping
    public ResponseEntity<List<TimetableResponse>> list(
            @RequestParam(required = false) Long termId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(timetableService.list(termId, status));
    }

    /** @return 200 if found; 404 if not found or in another tenant */
    @GetMapping("/{id}")
    public ResponseEntity<TimetableResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(timetableService.getById(id));
    }

    /**
     * Creates a DRAFT timetable for a term.
     *
     * @return 201 on success; 400 if no bell schedule can be resolved;
     *         404 if the term or an explicit bellScheduleId is not in the tenant
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<TimetableResponse> create(@Valid @RequestBody TimetableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(timetableService.create(request));
    }

    /**
     * Updates a timetable's name, term or bell schedule. Status is changed separately.
     *
     * @return 200 on success; 404 if not found; 409 if the timetable is ARCHIVED
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<TimetableResponse> update(
            @PathVariable Long id, @Valid @RequestBody TimetableRequest request) {
        return ResponseEntity.ok(timetableService.update(id, request));
    }

    /**
     * Moves the timetable along its lifecycle. Publishing archives the term's previously published
     * timetable, so only one stays PUBLISHED.
     *
     * @return 200 on success; 400 for an unknown status, a repeat of the current status, or a
     *         backwards transition; 404 if not found
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<TimetableResponse> changeStatus(
            @PathVariable Long id, @Valid @RequestBody TimetableStatusRequest request) {
        return ResponseEntity.ok(timetableService.changeStatus(id, request.status()));
    }

    /**
     * Hard-deletes a DRAFT timetable and its lessons.
     *
     * @return 204 on success; 404 if not found; 409 if the timetable is PUBLISHED or ARCHIVED
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        timetableService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
