package com.schediflow.api.v1;

import com.schediflow.dto.request.RoomRequest;
import com.schediflow.dto.response.RoomResponse;
import com.schediflow.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD endpoints for rooms within a tenant.
 * Write operations (POST, PUT, DELETE) are restricted to ADMIN and MOD roles.
 * Read operations (GET) are available to all authenticated users.
 */
@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    /**
     * Returns all active rooms for the authenticated tenant, ordered by name.
     *
     * @return 200 with list of rooms
     */
    @GetMapping
    public ResponseEntity<List<RoomResponse>> list() {
        return ResponseEntity.ok(roomService.list());
    }

    /**
     * Returns a single room by id.
     *
     * @return 200 if found; 404 if not found or belongs to a different tenant
     */
    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getById(id));
    }

    /**
     * Creates a new room for the tenant.
     *
     * @return 201 on success; 400 on validation failure or invalid type;
     *         409 if room name already exists in tenant
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<RoomResponse> create(
            @Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.create(request));
    }

    /**
     * Updates an existing room.
     *
     * @return 200 on success; 400 on validation failure or invalid type;
     *         404 if not found; 409 if updated name conflicts with another room
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<RoomResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody RoomRequest request) {
        return ResponseEntity.ok(roomService.update(id, request));
    }

    /**
     * Soft-deletes a room (sets isActive = false).
     *
     * @return 204 on success; 404 if not found or belongs to a different tenant
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roomService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
