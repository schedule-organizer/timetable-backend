package com.schediflow.api.v1;

import com.schediflow.dto.request.SubjectRequest;
import com.schediflow.dto.response.SubjectResponse;
import com.schediflow.service.SubjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD endpoints for subjects within a tenant.
 * Write operations (POST, PUT, DELETE) are restricted to ADMIN and MOD roles.
 * Read operations (GET) are available to all authenticated users.
 */
@RestController
@RequestMapping("/api/v1/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    /**
     * Returns all active subjects for the authenticated tenant, ordered by name.
     *
     * @return 200 with list of subjects
     */
    @GetMapping
    public ResponseEntity<List<SubjectResponse>> list() {
        return ResponseEntity.ok(subjectService.list());
    }

    /**
     * Returns a single subject by id.
     *
     * @return 200 if found; 404 if not found or belongs to a different tenant
     */
    @GetMapping("/{id}")
    public ResponseEntity<SubjectResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(subjectService.getById(id));
    }

    /**
     * Creates a new subject for the tenant.
     *
     * @return 201 on success; 400 on validation failure;
     *         409 if subject code already exists in tenant
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<SubjectResponse> create(@Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectService.create(request));
    }

    /**
     * Updates an existing subject.
     *
     * @return 200 on success; 400 on validation failure;
     *         404 if not found; 409 if updated code conflicts with another subject
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<SubjectResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.ok(subjectService.update(id, request));
    }

    /**
     * Soft-deletes a subject (sets isActive = false).
     *
     * @return 204 on success; 404 if not found; 409 if class subject hours reference this subject
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        subjectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
