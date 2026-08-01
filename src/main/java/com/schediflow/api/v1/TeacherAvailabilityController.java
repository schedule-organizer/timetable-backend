package com.schediflow.api.v1;

import com.schediflow.dto.response.TeacherAvailabilityResponse;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.service.TeacherAvailabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Combined availability view for one teacher: hard unavailability from forbidden slots plus soft
 * weekly preferences, rendered as a weekday × period grid.
 */
@RestController
@RequestMapping("/api/v1/teachers/{teacherId}/availability")
public class TeacherAvailabilityController {

    private final TeacherAvailabilityService teacherAvailabilityService;

    public TeacherAvailabilityController(TeacherAvailabilityService teacherAvailabilityService) {
        this.teacherAvailabilityService = teacherAvailabilityService;
    }

    /**
     * Returns the teacher's weekly availability grid.
     *
     * <p>ADMIN and MOD may read any teacher; other roles may only read the profile mapped to their
     * own user.</p>
     *
     * @return 200 with the grid; 400 if the tenant has no default bell schedule;
     *         403 if the caller is neither ADMIN/MOD nor the teacher themselves;
     *         404 if the teacher is not in the tenant
     */
    @GetMapping
    public ResponseEntity<TeacherAvailabilityResponse> getAvailability(
            @AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long teacherId) {
        return ResponseEntity.ok(teacherAvailabilityService.getAvailability(principal, teacherId));
    }
}
