# Story SCHED-05 — Cancel Solver Job
**Epic:** Epic 6 — Timetable & Scheduling Engine | **Points:** 2 SP | **Status:** Not Started

## Description
`POST /api/v1/engine/jobs/{id}/cancel` — cancel a running solver job; update status to CANCELLED

## Acceptance Criteria
- [ ] Calls `SolverManager.terminateEarly(jobId)`
- [ ] Updates job status to CANCELLED
- [ ] Returns 404 if job not found in tenant
- [ ] Returns 400 if job is already in terminal state (COMPLETED, FAILED, CANCELLED)
- [ ] Best solution found before cancellation is retained in DB
- [ ] Admin/Mod only

## Technical Notes
Timefold `SolverManager.terminateEarly` is non-blocking. Status updated via solver listener.
