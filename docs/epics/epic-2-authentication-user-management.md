# Epic 2 — Authentication & User Management
**Status:** Not Started | **MVP:** Yes | **Total Points:** 26 SP

## Goal
Implement the full authentication lifecycle — institution self-registration, email/password login, JWT access + refresh token flow, teacher invitation via email, and user profile management.

## Market Driver
The primary persona (Timetabler / Scheduling Coordinator) is non-technical. Onboarding must be frictionless. The invitation-based teacher registration model reflects how schools actually operate: administrators manage staff onboarding, not the staff themselves. OTP and SSO are post-MVP.

## Stories
| Story ID | Description | Points | Status |
|---|---|---|---|
| AUTH-01 | `POST /api/v1/auth/register` — institution self-registration: creates `Tenant` + first `Admin` user, returns JWT | 3 | Not Started |
| AUTH-02 | `POST /api/v1/auth/login` — email/password login, returns JWT access token (15 min) + `HttpOnly` refresh cookie (7 days) | 3 | Not Started |
| AUTH-03 | `POST /api/v1/auth/refresh` — validates refresh cookie, returns new access token | 2 | Not Started |
| AUTH-04 | `POST /api/v1/auth/logout` — invalidates (deletes) refresh token server-side | 1 | Not Started |
| AUTH-05 | `POST /api/v1/users/invite` — create `PENDING_REGISTRATION` user, generate single-use 72h token, send invite email via `SmtpEmailService` | 5 | Not Started |
| AUTH-06 | `POST /api/v1/auth/complete-registration` — consume invitation token, set password, activate user account | 4 | Not Started |
| AUTH-07 | `GET/PUT /api/v1/users/me` — read and update authenticated user's own profile | 3 | Not Started |
| AUTH-08 | `GET /api/v1/users` — paginated user list with role/status filters (Admin, Mod only) | 2 | Not Started |
| AUTH-09 | `PUT /api/v1/users/{id}/role` — role change (Admin only), cannot demote self | 2 | Not Started |
| AUTH-10 | `DELETE /api/v1/users/{id}` — soft deactivate (sets `status=INACTIVE`); does not delete records | 1 | Not Started |

## Notes
Rate-limit `/auth/**` at 10 req/min/IP. Registration tokens are single-use and expire after 72h. Passwords hashed with BCrypt cost factor 12.
