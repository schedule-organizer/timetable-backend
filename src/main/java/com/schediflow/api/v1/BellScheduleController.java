package com.schediflow.api.v1;

import com.schediflow.dto.request.BellScheduleRequest;
import com.schediflow.dto.response.BellScheduleResponse;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.service.BellScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD endpoints for bell schedules within a tenant.
 * All operations are restricted to ADMIN only — institution configuration is an
 * administrative surface and must not be accessible to MODERATOR or TEACHER roles.
 */
@RestController
@RequestMapping("/api/v1/bell-schedules")
public class BellScheduleController {

    private final BellScheduleService bellScheduleService;

    public BellScheduleController(BellScheduleService bellScheduleService) {
        this.bellScheduleService = bellScheduleService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BellScheduleResponse>> list() {
        return ResponseEntity.ok(bellScheduleService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BellScheduleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(bellScheduleService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BellScheduleResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody BellScheduleRequest request) {
        BellScheduleResponse body = bellScheduleService.create(principal.tenantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BellScheduleResponse> update(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody BellScheduleRequest request) {
        return ResponseEntity.ok(bellScheduleService.update(principal.tenantId(), id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id) {
        bellScheduleService.delete(principal.tenantId(), id);
        return ResponseEntity.noContent().build();
    }
}
