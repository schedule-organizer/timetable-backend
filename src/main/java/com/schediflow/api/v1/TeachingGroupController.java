package com.schediflow.api.v1;

import com.schediflow.dto.request.TeachingGroupRequest;
import com.schediflow.dto.response.TeachingGroupResponse;
import com.schediflow.service.TeachingGroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD endpoints for teaching groups — a teacher delivering a subject to one or more school classes.
 * Write operations (POST, PUT, DELETE) are restricted to ADMIN and MOD roles.
 * Read operations (GET) are available to all authenticated users.
 */
@RestController
@RequestMapping("/api/v1/teaching-groups")
public class TeachingGroupController {

    private final TeachingGroupService teachingGroupService;

    public TeachingGroupController(TeachingGroupService teachingGroupService) {
        this.teachingGroupService = teachingGroupService;
    }

    /**
     * Returns all active teaching groups for the authenticated tenant, ordered by name.
     *
     * @return 200 with list of teaching groups
     */
    @GetMapping
    public ResponseEntity<List<TeachingGroupResponse>> list() {
        return ResponseEntity.ok(teachingGroupService.list());
    }

    /**
     * Returns a single teaching group by id.
     *
     * @return 200 if found; 404 if not found or belongs to a different tenant
     */
    @GetMapping("/{id}")
    public ResponseEntity<TeachingGroupResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(teachingGroupService.getById(id));
    }

    /**
     * Creates a teaching group and its member-class links.
     *
     * @return 201 on success; 400 on validation failure (unknown type, wrong class count for the type);
     *         404 if the teacher, subject or any class is not in the tenant;
     *         409 if the teacher already teaches that subject to one of the classes
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<TeachingGroupResponse> create(@Valid @RequestBody TeachingGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teachingGroupService.create(request));
    }

    /**
     * Updates a teaching group, replacing its member-class links.
     *
     * @return 200 on success; 400 on validation failure; 404 if the group or a referenced entity is missing;
     *         409 on a duplicate teacher/subject/class combination
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<TeachingGroupResponse> update(
            @PathVariable Long id, @Valid @RequestBody TeachingGroupRequest request) {
        return ResponseEntity.ok(teachingGroupService.update(id, request));
    }

    /**
     * Soft-deletes a teaching group (sets isActive = false); member-class links are kept.
     *
     * @return 204 on success; 404 if not found
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        teachingGroupService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
