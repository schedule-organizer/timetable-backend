# Story COVER-03 — Submit Delegation Request
**Epic:** Epic 7 — Cover, Delegation & Temporary Schedules | **Points:** 3 SP | **Status:** Not Started

## Description
`POST /api/v1/delegation` — teacher submits SWAP or HANDOVER request for one or more lessons; Flyway migration

## Acceptance Criteria
- [ ] Accepts: type (SWAP/HANDOVER), lessonIds[], targetTeacherId (for SWAP), reason
- [ ] Creates delegation request with status PENDING
- [ ] SWAP: proposes exchange of lessons between two teachers
- [ ] HANDOVER: permanently transfers lesson responsibility
- [ ] Teacher can only delegate their own lessons
- [ ] `V00X__create_delegation_requests.sql` Flyway migration included
- [ ] Returns 404 if any lessonId or targetTeacherId not found in tenant

## Technical Notes
`delegation_requests` and `delegation_request_lessons` tables.
