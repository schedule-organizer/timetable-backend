package com.schediflow.api.v1;

import com.schediflow.dto.request.ChangeRoleRequest;
import com.schediflow.dto.request.InviteRequest;
import com.schediflow.dto.request.UpdateProfileRequest;
import com.schediflow.dto.response.PagedResponse;
import com.schediflow.dto.response.UserResponse;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Manages user administration within a tenant.
 * All endpoints require an authenticated JWT; role-specific access is enforced per method.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * POST /api/v1/users/invite
     *
     * <p>Invites a teacher by email. Creates a PENDING_REGISTRATION user and sends an
     * invitation link. Re-sending to the same email re-issues the token if the user is
     * still pending. Returns 409 if the email is already ACTIVE in this tenant.</p>
     *
     * @return 201 Created on success; 409 if email is already ACTIVE; 403 if caller lacks role
     */
    /**
     * GET /api/v1/users
     *
     * <p>Returns a paginated list of users in the caller's tenant.
     * Optional filters: {@code role}, {@code status}. Default page size 20, max 100.</p>
     *
     * @return 200 OK with {@link PagedResponse}; 403 if caller is TEACHER
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<PagedResponse<UserResponse>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(userService.listUsers(role, status, pageable));
    }

    /**
     * GET /api/v1/users/me
     *
     * <p>Returns the authenticated user's own profile. Password is never included.</p>
     *
     * @return 200 OK with {@link UserResponse}; 401 if unauthenticated
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(userService.getMe(principal.userId()));
    }

    /**
     * PUT /api/v1/users/me
     *
     * <p>Updates the authenticated user's display name and/or password.
     * If {@code newPassword} is provided, {@code currentPassword} must be correct.</p>
     *
     * @return 200 OK with updated {@link UserResponse}; 400 if current password is wrong
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {

        UserResponse updated = userService.updateMe(
                principal.userId(),
                request.displayName(),
                request.currentPassword(),
                request.newPassword());
        return ResponseEntity.ok(updated);
    }

    /**
     * POST /api/v1/users/invite
     *
     * <p>Invites a teacher by email. Creates a PENDING_REGISTRATION user and sends an
     * invitation link. Re-sending to the same email re-issues the token if the user is
     * still pending. Returns 409 if the email is already ACTIVE in this tenant.</p>
     *
     * @return 201 Created on success; 409 if email is already ACTIVE; 403 if caller lacks role
     */
    /**
     * DELETE /api/v1/users/{id}
     *
     * <p>Soft-deactivates a user by setting their status to INACTIVE and
     * invalidating all their refresh tokens. Admin/Mod only.
     * Cannot be used to deactivate the caller's own account.</p>
     *
     * @return 204 No Content on success; 400 if self-deactivation; 403 if TEACHER; 404 if user not found
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtPrincipal principal) {

        userService.deactivateUser(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /api/v1/users/{id}/role
     *
     * <p>Changes the role of a user within the caller's tenant. Admin only.
     * Cannot be used to change the caller's own role.</p>
     *
     * @return 200 OK with updated {@link UserResponse}; 400 if self-change or invalid role; 403 if not Admin; 404 if user not found
     */
    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {

        return ResponseEntity.ok(userService.changeRole(principal.userId(), id, request.role()));
    }

    @PostMapping("/invite")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<Void> invite(
            @Valid @RequestBody InviteRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {

        userService.invite(principal.tenantId(), request.email());
        return ResponseEntity.status(201).build();
    }
}
