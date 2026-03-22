# Story AUTH-06 — Complete Registration via Invite
**Epic:** Epic 2 — Authentication & User Management | **Points:** 4 SP | **Status:** Not Started

## Description
`POST /api/v1/auth/complete-registration` — consume invitation token, set password, activate user account

## Acceptance Criteria
- [ ] Accepts: invitation token, new password, optional display name
- [ ] Validates token exists, is not expired, and has not been used
- [ ] Sets user password (BCrypt hashed), status → `ACTIVE`
- [ ] Marks token as consumed (single-use)
- [ ] Returns JWT access token + sets refresh cookie (auto-login after registration)
- [ ] Returns 400 for expired or invalid token with clear error message

## Technical Notes
Runs in a single transaction. Token lookup by hash.
