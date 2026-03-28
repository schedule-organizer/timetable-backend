# Story COVER-01 — Assign Cover Teacher
**Epic:** Epic 7 — Cover, Delegation & Temporary Schedules | **Points:** 4 SP | **Status:** Not Started

## Description
`POST /api/v1/cover` — assign cover teacher to a lesson; validate qualification for subject and no timetable conflict; Flyway migration

## Acceptance Criteria
- [ ] Accepts: lessonId, coverTeacherId, reason (optional)
- [ ] Validates cover teacher is qualified for the lesson's subject
- [ ] Validates cover teacher has no timetable conflict in that period
- [ ] Validates cover teacher has no forbidden slot for that period
- [ ] Creates cover assignment record; original teacher remains on record
- [ ] `V00X__create_cover_assignments.sql` Flyway migration included
- [ ] Triggers `COVER_ASSIGNED` WebSocket event (COVER-07)

## Technical Notes
`cover_assignments` table with lesson_id, cover_teacher_id, assigned_by, assigned_at.
