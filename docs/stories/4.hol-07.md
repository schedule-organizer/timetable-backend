# Story HOL-07 — Holiday Conflict Detection
**Epic:** Epic 4 — Holiday & Vacation Calendar | **Points:** 3 SP | **Status:** Not Started

## Description
Conflict detection: `ConflictDetectionService` warns when a published lesson falls on a newly added holiday date

## Acceptance Criteria
- [ ] When a new holiday date is added (manual or imported), checks all PUBLISHED timetables for affected term
- [ ] Returns list of affected lessons (lessonId, subject, teacher, class, conflicting date)
- [ ] Does not auto-cancel lessons — returns warnings only
- [ ] Triggered on both HOL-02 (import) and HOL-03 (manual add) operations
- [ ] Returns empty list if no conflicts found

## Technical Notes
Integrates with `ConflictDetectionService` from SCHED-11.
