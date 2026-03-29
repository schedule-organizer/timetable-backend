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

@RestController
@RequestMapping("/api/v1/bell-schedules")
public class BellScheduleController {

    private final BellScheduleService bellScheduleService;

    public BellScheduleController(BellScheduleService bellScheduleService) {
        this.bellScheduleService = bellScheduleService;
    }

    @GetMapping
    public ResponseEntity<List<BellScheduleResponse>> list() {
        return ResponseEntity.ok(bellScheduleService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BellScheduleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(bellScheduleService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<BellScheduleResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody BellScheduleRequest request) {
        BellScheduleResponse body = bellScheduleService.create(principal.tenantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<BellScheduleResponse> update(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody BellScheduleRequest request) {
        return ResponseEntity.ok(bellScheduleService.update(principal.tenantId(), id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id) {
        bellScheduleService.delete(principal.tenantId(), id);
        return ResponseEntity.noContent().build();
    }
}
