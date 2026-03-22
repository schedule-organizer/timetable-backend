# Story AUTH-04 — Logout
**Epic:** Epic 2 — Authentication & User Management | **Points:** 1 SP | **Status:** Not Started

## Description
`POST /api/v1/auth/logout` — invalidates (deletes) refresh token server-side

## Acceptance Criteria
- [ ] Deletes refresh token record from DB
- [ ] Clears the HttpOnly refresh cookie (Set-Cookie with expired date)
- [ ] Returns 200 even if no refresh token cookie present (idempotent)
- [ ] Requires valid JWT access token (authenticated endpoint)

## Technical Notes
No body required. Cookie cleared by setting max-age=0.
