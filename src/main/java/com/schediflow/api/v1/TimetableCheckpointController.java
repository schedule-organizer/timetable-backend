package com.schediflow.api.v1;

import com.schediflow.dto.request.CheckpointRequest;
import com.schediflow.dto.response.CheckpointResponse;
import com.schediflow.dto.response.PagedResponse;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.service.TimetableCheckpointService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.function.Function;

/**
 * Timetable checkpoints — save a named snapshot, list them, roll back to one (SCHED-13).
 * ADMIN and MOD only: restoring rewrites everyone's schedule.
 */
@RestController
@RequestMapping("/api/v1/timetables/{timetableId}/checkpoints")
public class TimetableCheckpointController {

    private final TimetableCheckpointService checkpointService;

    public TimetableCheckpointController(TimetableCheckpointService checkpointService) {
        this.checkpointService = checkpointService;
    }

    /** @return 201 with the checkpoint; 404 if the timetable is not in the tenant */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<CheckpointResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long timetableId,
            @Valid @RequestBody CheckpointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(checkpointService.create(principal, timetableId, request));
    }

    /** @return 200 with a page of checkpoints, newest first */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<PagedResponse<CheckpointResponse>> list(
            @PathVariable Long timetableId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CheckpointResponse> checkpoints =
                checkpointService.list(timetableId, PageRequest.of(page, Math.min(size, 100)));
        return ResponseEntity.ok(PagedResponse.from(checkpoints, Function.identity()));
    }

    /**
     * Rolls the timetable back to a checkpoint.
     *
     * @return 200 with the checkpoint restored from; 404 if the timetable or checkpoint is missing;
     *         409 if the timetable is not a DRAFT
     */
    @PostMapping("/{checkpointId}/restore")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<CheckpointResponse> restore(
            @PathVariable Long timetableId, @PathVariable Long checkpointId) {
        return ResponseEntity.ok(checkpointService.restore(timetableId, checkpointId));
    }
}
