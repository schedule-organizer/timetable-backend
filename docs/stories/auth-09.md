# Story AUTH-09 — Role Change
**Epic:** Epic 2 — Authentication & User Management | **Points:** 2 SP | **Status:** Not Started

## Description
`PUT /api/v1/users/{id}/role` — role change (Admin only), cannot demote self

## Acceptance Criteria
- [ ] Accepts target `role` in request body
- [ ] Admin only; returns 403 for non-Admin callers
- [ ] Returns 400 if attempting to change own role
- [ ] Returns 404 if user not found within tenant
- [ ] Valid roles: ADMIN, MODERATOR, TEACHER
- [ ] Returns updated user profile on success

## Technical Notes
Multi-tenancy filter ensures cannot modify users in other tenants.
