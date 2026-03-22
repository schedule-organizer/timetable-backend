# Story AUTH-05 — Teacher Invitation
**Epic:** Epic 2 — Authentication & User Management | **Points:** 5 SP | **Status:** Not Started

## Description
`POST /api/v1/users/invite` — create `PENDING_REGISTRATION` user, generate single-use 72h token, send invite email via `SmtpEmailService`

## Acceptance Criteria
- [ ] Admin/Mod creates user with status `PENDING_REGISTRATION`
- [ ] Generates single-use invitation token (UUID or secure random, stored hashed)
- [ ] Token expires after 72 hours
- [ ] Sends invitation email via `SmtpEmailService` with registration link containing token
- [ ] Returns 409 if email already exists in tenant
- [ ] Invitation can be re-sent (generates new token, invalidates old)

## Technical Notes
Requires NOTIF-03 (`SmtpEmailService`) or stub. Token stored in `invitation_tokens` table.
