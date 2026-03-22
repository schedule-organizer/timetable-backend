# Story AUTH-10 — Soft Deactivate User
**Epic:** Epic 2 — Authentication & User Management | **Points:** 1 SP | **Status:** Not Started

## Description
`DELETE /api/v1/users/{id}` — soft deactivate (sets `status=INACTIVE`); does not delete records

## Acceptance Criteria
- [ ] Sets user `status` to `INACTIVE` (no hard delete)
- [ ] Admin/Mod only; returns 403 for TEACHER role
- [ ] Returns 400 if attempting to deactivate self
- [ ] Deactivated user cannot log in (AUTH-02 returns 401)
- [ ] Returns 404 if user not found within tenant
- [ ] Existing refresh tokens for deactivated user are invalidated

## Technical Notes
Cascades token invalidation. Audit log entry created.
