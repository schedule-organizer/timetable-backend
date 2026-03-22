# Story COVER-05 — Temporary Schedules CRUD
**Epic:** Epic 7 — Cover, Delegation & Temporary Schedules | **Points:** 5 SP | **Status:** Not Started

## Description
CRUD `/api/v1/temporary-schedules` — create named temporary schedule overlaying a base timetable for a date range; Flyway migration

## Acceptance Criteria
- [ ] CRUD for temporary schedules (name, baseTimetableId, startDate, endDate)
- [ ] Temporary schedule overlays base timetable for its date range
- [ ] Lesson overrides within a temporary schedule stored separately from base timetable lessons
- [ ] Only one active temporary schedule per timetable at a time
- [ ] `V00X__create_temporary_schedules.sql` Flyway migration included
- [ ] Returns 400 if startDate >= endDate or dates outside term range

## Technical Notes
`temporary_schedules` and `temporary_schedule_lessons` tables. Base lessons not mutated.
