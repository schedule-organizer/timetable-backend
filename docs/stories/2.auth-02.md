# Story AUTH-02 — Email/Password Login
**Epic:** Epic 2 — Authentication & User Management | **Points:** 3 SP | **Status:** Not Started

## Description
`POST /api/v1/auth/login` — email/password login, returns JWT access token (15 min) + `HttpOnly` refresh cookie (7 days)

## Acceptance Criteria
- [ ] Endpoint accepts: email, password
- [ ] Returns JWT access token (15 min expiry) in response body
- [ ] Sets `HttpOnly`, `Secure`, `SameSite=Strict` refresh token cookie (7 days)
- [ ] Returns 401 for invalid credentials (no distinction between wrong email vs password)
- [ ] Returns 401 for INACTIVE user accounts
- [ ] Rate limited: 10 req/min/IP on `/auth/**`

## Technical Notes
Refresh token stored in `refresh_tokens` table. BCrypt comparison with cost factor 12.
