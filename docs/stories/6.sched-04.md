# Story SCHED-04 — Solver Job Status Polling
**Epic:** Epic 6 — Timetable & Scheduling Engine | **Points:** 2 SP | **Status:** Not Started

## Description
`GET /api/v1/engine/jobs/{id}` and `GET /api/v1/engine/jobs` — poll job status, quality score, score breakdown, error message; job history per timetable

## Acceptance Criteria
- [ ] `GET /api/v1/engine/jobs/{id}` returns: jobId, timetableId, status, qualityScore, scoreBreakdown, startedAt, completedAt, errorMessage
- [ ] Job statuses: QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED
- [ ] `GET /api/v1/engine/jobs?timetableId=...` returns paginated job history
- [ ] Returns 404 if job not found in tenant
- [ ] Quality score formatted as: hard violations count + soft score
- [ ] Score breakdown lists top constraint violations

## Technical Notes
`solver_jobs` table. Solver listener updates status on each best-solution event.
