# Story HOL-05 — Solver Holiday Integration
**Epic:** Epic 4 — Holiday & Vacation Calendar | **Points:** 3 SP | **Status:** Not Started

## Description
Solver integration: `HolidayService` loads holiday dates and converts them to globally forbidden period slots before each solver run

## Acceptance Criteria
- [ ] `HolidayService.getForbiddenSlots(timetableId)` returns list of forbidden `PeriodSlot` objects
- [ ] Holiday dates converted to all period slots on that date
- [ ] Forbidden slots passed to Timefold solver as hard constraints
- [ ] Solver rejects solutions that schedule lessons on holiday dates
- [ ] Performance: slot loading completes in < 100ms for up to 365 holiday dates

## Technical Notes
Depends on SCHED-03. Holiday slots represented as `UnavailablePeriodPenalty` in Timefold model.
