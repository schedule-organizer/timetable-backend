package com.schediflow.api.v1;

import com.schediflow.dto.request.SchoolClassRequest;
import com.schediflow.dto.response.SchoolClassResponse;
import com.schediflow.service.SchoolClassService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD endpoints for school classes within a tenant.
 * Write operations (POST, PUT, DELETE) are restricted to ADMIN and MOD roles.
 * Read operations (GET) are available to all authenticated users.
 */
@RestController
@RequestMapping("/api/v1/classes")
public class SchoolClassController {

    private final SchoolClassService schoolClassService;

    public SchoolClassController(SchoolClassService schoolClassService) {
        this.schoolClassService = schoolClassService;
    }

    /**
     * Returns all active school classes for the authenticated tenant, ordered by name.
     *
     * @return 200 with list of school classes
     */
    @GetMapping
    public ResponseEntity<List<SchoolClassResponse>> list() {
        return ResponseEntity.ok(schoolClassService.list());
    }

    /**
     * Returns a single school class by id.
     *
     * @return 200 if found; 404 if not found or belongs to a different tenant
     */
    @GetMapping("/{id}")
    public ResponseEntity<SchoolClassResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(schoolClassService.getById(id));
    }

    /**
     * Creates a new school class for the tenant.
     *
     * @return 201 on success; 400 on validation failure;
     *         409 if class name already exists in tenant
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<SchoolClassResponse> create(@Valid @RequestBody SchoolClassRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schoolClassService.create(request));
    }

    /**
     * Updates an existing school class.
     *
     * @return 200 on success; 400 on validation failure;
     *         404 if not found; 409 if updated name conflicts with another class
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<SchoolClassResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SchoolClassRequest request) {
        return ResponseEntity.ok(schoolClassService.update(id, request));
    }

    /**
     * Soft-deletes a school class (sets isActive = false).
     *
     * @return 204 on success; 404 if not found; 409 if class has timetable assignments
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        schoolClassService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
