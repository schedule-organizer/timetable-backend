package com.schediflow.api.v1;

import com.schediflow.dto.request.ForbiddenSlotRequest;
import com.schediflow.dto.response.ForbiddenSlotResponse;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.service.ForbiddenSlotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Hard unavailability for teachers, rooms and classes.
 *
 * <p>ADMIN and MOD may manage slots for any entity. Other roles may only read and manage slots for
 * the TEACHER entity that maps to their own user, so a teacher can declare their own unavailability
 * (FR35) without seeing or editing anyone else's.</p>
 */
@RestController
@RequestMapping("/api/v1/forbidden-slots")
public class ForbiddenSlotController {

    private final ForbiddenSlotService forbiddenSlotService;

    public ForbiddenSlotController(ForbiddenSlotService forbiddenSlotService) {
        this.forbiddenSlotService = forbiddenSlotService;
    }

    /**
     * Lists the forbidden slots of one entity.
     *
     * @return 200 with the entity's slots; 400 for an unknown entityType;
     *         403 if a teacher asks for an entity that is not their own; 404 if the entity is not in the tenant
     */
    @GetMapping
    public ResponseEntity<List<ForbiddenSlotResponse>> list(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam String entityType,
            @RequestParam Long entityId) {
        return ResponseEntity.ok(forbiddenSlotService.list(principal, entityType, entityId));
    }

    /**
     * Creates a forbidden slot, recurring (dayOfWeek) or one-off (specificDate).
     *
     * @return 201 on success; 400 on validation failure or a recurrence/date mismatch;
     *         403 if a teacher targets another entity; 404 if the entity or period is not in the tenant;
     *         409 if the identical slot is already forbidden
     */
    @PostMapping
    public ResponseEntity<ForbiddenSlotResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal, @Valid @RequestBody ForbiddenSlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(forbiddenSlotService.create(principal, request));
    }

    /**
     * Removes a forbidden slot.
     *
     * @return 204 on success; 403 if a teacher targets another entity's slot; 404 if not found in the tenant
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long id) {
        forbiddenSlotService.delete(principal, id);
        return ResponseEntity.noContent().build();
    }
}
