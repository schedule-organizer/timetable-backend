# Story AUTH-07 — Own Profile Read/Update
**Epic:** Epic 2 — Authentication & User Management | **Points:** 3 SP | **Status:** Not Started

## Description
`GET/PUT /api/v1/users/me` — read and update authenticated user's own profile

## Acceptance Criteria
- [ ] `GET /api/v1/users/me` returns current user's profile (id, email, displayName, role, status)
- [ ] `PUT /api/v1/users/me` allows updating: displayName, password (requires current password confirmation)
- [ ] Email is not changeable via this endpoint
- [ ] Returns 401 if unauthenticated
- [ ] Password update: validates current password before accepting new password

## Technical Notes
Password field never returned in response. Use `UserResponseDto` without password.
