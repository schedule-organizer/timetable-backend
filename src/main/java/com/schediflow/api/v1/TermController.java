package com.schediflow.api.v1;

import com.schediflow.dto.request.TermRequest;
import com.schediflow.dto.response.TermResponse;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.service.TermService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/terms")
public class TermController {

    private final TermService termService;

    public TermController(TermService termService) {
        this.termService = termService;
    }

    @GetMapping
    public ResponseEntity<List<TermResponse>> list(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam Long academicYearId) {
        return ResponseEntity.ok(termService.list(principal.tenantId(), academicYearId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TermResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(termService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<TermResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal, @Valid @RequestBody TermRequest request) {
        TermResponse body = termService.create(principal.tenantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<TermResponse> update(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody TermRequest request) {
        return ResponseEntity.ok(termService.update(principal.tenantId(), id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        termService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
