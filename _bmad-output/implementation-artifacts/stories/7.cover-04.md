# Story COVER-04 — Approve/Reject Delegation
**Epic:** Epic 7 — Cover, Delegation & Temporary Schedules | **Points:** 3 SP | **Status:** Not Started

## Description
`PATCH /api/v1/delegation/{id}` — moderator approves or rejects delegation request; on approval, atomically reassigns lessons

## Acceptance Criteria
- [ ] Accepts: decision (APPROVED/REJECTED), rejectionReason (required if REJECTED)
- [ ] APPROVED: atomically reassigns all lesson records to target teacher
- [ ] APPROVED SWAP: atomically swaps lesson assignments between both teachers
- [ ] Validates no conflicts introduced by approval before committing
- [ ] Notifies both teachers via `DELEGATION_UPDATE` WebSocket event (COVER-07)
- [ ] Moderator/Admin only
- [ ] Returns 400 if request is already in terminal state

## Technical Notes
Single DB transaction for all lesson reassignments. Conflict check pre-commit.
