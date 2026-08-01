package com.schediflow.api.v1;

import com.schediflow.dto.request.ClassSubjectHoursReplaceRequest;
import com.schediflow.dto.response.ClassSubjectHourResponse;
import com.schediflow.service.ClassSubjectHourService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Class × subject weekly hours matrix (periods per cycle and spread pattern per subject).
 * Reads are available to all authenticated users; writes require ADMIN or MOD.
 */
@RestController
@RequestMapping("/api/v1/classes/{classId}/subject-hours")
public class ClassSubjectHourController {

    private final ClassSubjectHourService classSubjectHourService;

    public ClassSubjectHourController(ClassSubjectHourService classSubjectHourService) {
        this.classSubjectHourService = classSubjectHourService;
    }

    /**
     * Returns all subject-hour allocations for the class.
     *
     * @return 200 with list of allocations; 404 if the class is missing or not in the tenant
     */
    @GetMapping
    public ResponseEntity<List<ClassSubjectHourResponse>> list(@PathVariable Long classId) {
        return ResponseEntity.ok(classSubjectHourService.list(classId));
    }

    /**
     * Replaces the full subject-hours matrix for the class in one transaction.
     *
     * @return 200 with the persisted allocations; 400 if validation fails (e.g. over capacity, duplicate subjects);
     *         404 if the class or a referenced subject is missing; 403 if the caller lacks MOD/ADMIN
     */
    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<List<ClassSubjectHourResponse>> replace(
            @PathVariable Long classId, @Valid @RequestBody ClassSubjectHoursReplaceRequest request) {
        return ResponseEntity.ok(classSubjectHourService.replace(classId, request));
    }
}
