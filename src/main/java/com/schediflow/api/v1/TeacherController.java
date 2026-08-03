package com.schediflow.api.v1;

import com.schediflow.dto.request.TeacherRequest;
import com.schediflow.dto.response.TeacherResponse;
import com.schediflow.service.TeacherService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD endpoints for teacher profiles within a tenant.
 * Write operations (POST, PUT, DELETE) are restricted to ADMIN and MODERATOR roles.
 * Read operations (GET) are available to all authenticated users.
 */
@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    /**
     * Returns all active teacher profiles for the authenticated tenant, ordered by display name.
     *
     * @return 200 with list of teachers
     */
    @GetMapping
    public ResponseEntity<List<TeacherResponse>> list() {
        return ResponseEntity.ok(teacherService.list());
    }

    /**
     * Returns a single teacher profile by id.
     *
     * @return 200 if found; 404 if not found or belongs to a different tenant
     */
    @GetMapping("/{id}")
    public ResponseEntity<TeacherResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(teacherService.getById(id));
    }

    /**
     * Creates a teacher profile linked to a user in the tenant.
     *
     * @return 201 on success; 400 on validation failure; 404 if userId not found in tenant;
     *         409 if that user already has a teacher profile
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<TeacherResponse> create(@Valid @RequestBody TeacherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teacherService.create(request));
    }

    /**
     * Updates an existing teacher profile.
     *
     * @return 200 on success; 400 on validation failure; 404 if teacher or referenced user not found;
     *         409 if the target user already has another teacher profile
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<TeacherResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TeacherRequest request) {
        return ResponseEntity.ok(teacherService.update(id, request));
    }

    /**
     * Soft-deletes a teacher profile (sets isActive = false).
     *
     * @return 204 on success; 404 if not found; 409 if the teacher has persisted lesson assignments
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        teacherService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
