package com.schediflow.api.v1;

import com.schediflow.dto.request.DelegationDecisionRequest;
import com.schediflow.dto.request.DelegationRequestSubmission;
import com.schediflow.dto.response.DelegationRequestResponse;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.service.DelegationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delegation requests — a teacher asking to swap lessons with, or hand them over to, a colleague.
 *
 * <p>Any authenticated user may submit, but only for lessons they teach themselves.</p>
 */
@RestController
@RequestMapping("/api/v1/delegation")
public class DelegationController {

    private final DelegationService delegationService;

    public DelegationController(DelegationService delegationService) {
        this.delegationService = delegationService;
    }

    /**
     * Submits a delegation request in PENDING state.
     *
     * @return 201 on success; 400 for an unknown type, duplicate lesson ids, or self-delegation;
     *         403 if any lesson is not the caller's own; 404 if a lesson or the target teacher is
     *         not in the tenant; 409 if a lesson already has a pending request
     */
    @PostMapping
    public ResponseEntity<DelegationRequestResponse> submit(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody DelegationRequestSubmission request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(delegationService.submit(principal, request));
    }

    /**
     * Approves or rejects a pending request. An approval reassigns every affected lesson atomically.
     *
     * @return 200 with the decided request; 400 for an unknown decision, a missing rejection reason,
     *         or a request already in a terminal state; 403 without ADMIN/MOD;
     *         404 if the request is not in the tenant;
     *         409 if approving would double-book a teacher
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<DelegationRequestResponse> decide(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody DelegationDecisionRequest request) {
        return ResponseEntity.ok(delegationService.decide(principal, id, request));
    }
}
