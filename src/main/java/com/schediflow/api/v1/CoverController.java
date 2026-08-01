package com.schediflow.api.v1;

import com.schediflow.dto.request.CoverAssignmentRequest;
import com.schediflow.dto.response.CoverAssignmentResponse;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.service.CoverAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cover assignments — standing a different teacher in front of an existing lesson.
 * Restricted to ADMIN and MOD, as arranging cover is a timetable-management action.
 */
@RestController
@RequestMapping("/api/v1/cover")
public class CoverController {

    private final CoverAssignmentService coverAssignmentService;

    public CoverController(CoverAssignmentService coverAssignmentService) {
        this.coverAssignmentService = coverAssignmentService;
    }

    /**
     * Assigns a cover teacher to a lesson. The lesson's own teacher is left untouched.
     *
     * @return 201 on success; 400 if the teacher is unqualified or is the lesson's own teacher;
     *         404 if the lesson or teacher is not in the tenant;
     *         409 if the lesson already has cover, or the teacher is busy or forbidden in that period
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<CoverAssignmentResponse> assign(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CoverAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(coverAssignmentService.assign(principal, request));
    }
}
