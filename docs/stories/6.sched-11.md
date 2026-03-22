# Story SCHED-11 — Conflict Detection Service
**Epic:** Epic 6 — Timetable & Scheduling Engine | **Points:** 5 SP | **Status:** Not Started

## Description
`ConflictDetectionService` — real-time constraint checks: teacher double-booking, room double-booking, class double-booking, room capacity, forbidden slots

## Acceptance Criteria
- [ ] `checkConflicts(lesson, proposedPeriodId, proposedRoomId)` returns list of `ConflictViolation` objects
- [ ] Checks: teacher already assigned in period, room already booked in period, class already scheduled in period, room capacity < group size, teacher/room/class forbidden slot
- [ ] Returns empty list if no conflicts
- [ ] Used by SCHED-08 (move), SCHED-10 (swap), HOL-07 (holiday conflict)
- [ ] Performance: completes in < 50ms for typical school size (< 500 lessons)

## Technical Notes
Read-only service, no DB writes. Queries scoped to timetable via tenant filter.
