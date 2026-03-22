# Story AUTH-03 — Token Refresh
**Epic:** Epic 2 — Authentication & User Management | **Points:** 2 SP | **Status:** Not Started

## Description
`POST /api/v1/auth/refresh` — validates refresh cookie, returns new access token

## Acceptance Criteria
- [ ] Reads refresh token from HttpOnly cookie
- [ ] Returns new JWT access token if refresh token is valid and not expired
- [ ] Returns 401 if refresh token is missing, invalid, or expired
- [ ] Does not rotate refresh token (rotation is post-MVP)
- [ ] Returns 401 if associated user account is INACTIVE

## Technical Notes
Refresh token lookup by token hash in DB. Validate expiry in DB, not just JWT claim.
