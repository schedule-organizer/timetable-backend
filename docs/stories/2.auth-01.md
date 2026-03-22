# Story AUTH-01 — Institution Self-Registration
**Epic:** Epic 2 — Authentication & User Management | **Points:** 3 SP | **Status:** Not Started

## Description
`POST /api/v1/auth/register` — institution self-registration: creates `Tenant` + first `Admin` user, returns JWT

## Acceptance Criteria
- [ ] Endpoint accepts: institution name, admin email, admin password
- [ ] Creates `Tenant` record with unique slug derived from institution name
- [ ] Creates first `User` with role `ADMIN` linked to the new tenant
- [ ] Returns JWT access token + sets HttpOnly refresh cookie
- [ ] Returns 409 if email already registered
- [ ] Password validated: min 8 chars, at least one number
- [ ] Triggers `CONFIG-09` seed service to apply institution defaults

## Technical Notes
Runs in a single transaction. Slug must be URL-safe and unique.
