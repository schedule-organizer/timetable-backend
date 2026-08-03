package com.schediflow.api.v1;

import com.schediflow.dto.request.TeacherQualificationRequest;
import com.schediflow.dto.response.TeacherQualificationResponse;
import com.schediflow.service.TeacherQualificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teachers/{teacherId}/qualifications")
public class TeacherQualificationController {

    private final TeacherQualificationService qualificationService;

    public TeacherQualificationController(TeacherQualificationService qualificationService) {
        this.qualificationService = qualificationService;
    }

    @GetMapping
    public ResponseEntity<List<TeacherQualificationResponse>> list(@PathVariable Long teacherId) {
        return ResponseEntity.ok(qualificationService.list(teacherId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<TeacherQualificationResponse> add(
            @PathVariable Long teacherId, @Valid @RequestBody TeacherQualificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(qualificationService.add(teacherId, request));
    }

    @DeleteMapping("/{qualificationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<Void> delete(@PathVariable Long teacherId, @PathVariable Long qualificationId) {
        qualificationService.delete(teacherId, qualificationId);
        return ResponseEntity.noContent().build();
    }
}
