# Story SCHED-03 — Async Solver Engine
**Epic:** Epic 6 — Timetable & Scheduling Engine | **Points:** 8 SP | **Status:** Not Started

## Description
`POST /api/v1/engine/run` — async solver job: load problem from DB → build `TimetableSolution` → call `SolverManager.solveAndListen` → return `jobId` immediately; sensitivity and mode parameters

## Acceptance Criteria
- [ ] Accepts: timetableId, mode (FAST/BALANCED/THOROUGH), timeout override (optional)
- [ ] Loads all resources (teachers, rooms, classes, subjects, forbidden slots, holidays) from DB
- [ ] Builds Timefold `TimetableSolution` domain model
- [ ] Starts async solver via `SolverManager.solveAndListen`
- [ ] Returns `jobId` immediately (non-blocking)
- [ ] Only one active solver job per timetable at a time (409 if already running)
- [ ] Timeout enforced per subscription tier (Starter: 30s, Professional: 2min, Enterprise: 10min)

## Technical Notes
In-process JVM solver (TD-01). `AsyncConfig` isolates solver thread pool. Solver constraints defined as Timefold `ConstraintProvider`.
