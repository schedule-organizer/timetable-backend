# Story AUTH-08 — Paginated User List
**Epic:** Epic 2 — Authentication & User Management | **Points:** 2 SP | **Status:** Not Started

## Description
`GET /api/v1/users` — paginated user list with role/status filters (Admin, Mod only)

## Acceptance Criteria
- [ ] Returns paginated list of users within the authenticated user's tenant
- [ ] Supports query params: `role`, `status`, `page`, `size`, `sort`
- [ ] Accessible by ADMIN and MODERATOR roles only; returns 403 for TEACHER role
- [ ] Response includes: id, email, displayName, role, status, createdAt
- [ ] Default page size: 20; max page size: 100

## Technical Notes
Multi-tenancy filter automatically scopes results to tenant. Use Spring Data `Pageable`.
