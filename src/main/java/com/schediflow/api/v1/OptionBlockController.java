package com.schediflow.api.v1;

import com.schediflow.dto.request.OptionBlockRequest;
import com.schediflow.dto.response.OptionBlockResponse;
import com.schediflow.service.OptionBlockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD endpoints for option blocks — sets of teaching groups the solver must schedule in the same period.
 * Write operations (POST, PUT, DELETE) are restricted to ADMIN and MODERATOR roles.
 * Read operations (GET) are available to all authenticated users.
 */
@RestController
@RequestMapping("/api/v1/option-blocks")
public class OptionBlockController {

    private final OptionBlockService optionBlockService;

    public OptionBlockController(OptionBlockService optionBlockService) {
        this.optionBlockService = optionBlockService;
    }

    /**
     * Returns all active option blocks for the authenticated tenant, ordered by name.
     *
     * @return 200 with list of option blocks
     */
    @GetMapping
    public ResponseEntity<List<OptionBlockResponse>> list() {
        return ResponseEntity.ok(optionBlockService.list());
    }

    /**
     * Returns a single option block by id.
     *
     * @return 200 if found; 404 if not found or belongs to a different tenant
     */
    @GetMapping("/{id}")
    public ResponseEntity<OptionBlockResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(optionBlockService.getById(id));
    }

    /**
     * Creates an option block from at least two OPTION_BLOCK teaching groups.
     *
     * @return 201 on success; 400 if fewer than two members or a member is not of type OPTION_BLOCK;
     *         404 if a member group is not in the tenant; 409 if a member already belongs to another block
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<OptionBlockResponse> create(@Valid @RequestBody OptionBlockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(optionBlockService.create(request));
    }

    /**
     * Updates an option block, replacing its membership.
     *
     * @return 200 on success; 400 on validation failure; 404 if the block or a member group is missing;
     *         409 if a member already belongs to another block
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<OptionBlockResponse> update(
            @PathVariable Long id, @Valid @RequestBody OptionBlockRequest request) {
        return ResponseEntity.ok(optionBlockService.update(id, request));
    }

    /**
     * Soft-deletes an option block and releases its member groups.
     *
     * @return 204 on success; 404 if not found
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        optionBlockService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
