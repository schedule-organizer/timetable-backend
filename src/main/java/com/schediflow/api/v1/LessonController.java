package com.schediflow.api.v1;

import com.schediflow.dto.request.LessonMoveRequest;
import com.schediflow.dto.request.LessonSwapRequest;
import com.schediflow.dto.response.LessonResponse;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.service.LessonService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Direct edits to a placed lesson. ADMIN and MOD may edit any lesson; other roles only their own,
 * so a teacher can rearrange their own cards without touching anyone else's.
 */
@RestController
@RequestMapping("/api/v1/lessons")
public class LessonController {

    private final LessonService lessonService;

    public LessonController(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    /**
     * Moves a lesson to a new period and/or room (SCHED-08). Conflicts are reported in the response
     * rather than blocking the move.
     *
     * @return 200 with the updated lesson and any conflicts; 400 if neither field is supplied;
     *         403 if the lesson is not the caller's; 404 if the lesson, period or room is missing
     */
    @PatchMapping("/{id}")
    public ResponseEntity<LessonResponse> move(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody LessonMoveRequest request) {
        return ResponseEntity.ok(lessonService.move(principal, id, request));
    }

    /** @return 200 with the pinned lesson; 403 if not the caller's; 404 if not found */
    @PostMapping("/{id}/pin")
    public ResponseEntity<LessonResponse> pin(
            @AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(lessonService.setPinned(principal, id, true));
    }

    /** @return 200 with the unpinned lesson; 403 if not the caller's; 404 if not found */
    @DeleteMapping("/{id}/pin")
    public ResponseEntity<LessonResponse> unpin(
            @AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(lessonService.setPinned(principal, id, false));
    }

    /**
     * Exchanges the period and room of two lessons atomically (SCHED-10).
     *
     * @return 200 with both updated lessons; 400 if the swap would create a conflict on either side;
     *         403 if either lesson is not the caller's; 404 if either lesson is missing
     */
    @PostMapping("/{id}/swap")
    public ResponseEntity<List<LessonResponse>> swap(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody LessonSwapRequest request) {
        return ResponseEntity.ok(lessonService.swap(principal, id, request.targetLessonId()));
    }
}
