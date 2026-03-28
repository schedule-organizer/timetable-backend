# Story SCHED-02 — Timetable Lesson Grid
**Epic:** Epic 6 — Timetable & Scheduling Engine | **Points:** 3 SP | **Status:** Not Started

## Description
`GET /api/v1/timetables/{id}/lessons` — full lesson list for grid rendering, includes room, teacher, period, pin status, conflict flag

## Acceptance Criteria
- [ ] Returns all lessons for a timetable with: lessonId, subjectName, teacherName, roomName, periodId, dayOfWeek, isPinned, hasConflict
- [ ] Returns 404 if timetable not found in tenant
- [ ] Supports filter by teacherId, classId, roomId
- [ ] `hasConflict` flag set by `ConflictDetectionService` (SCHED-11)
- [ ] Response optimised: single query with joins (no N+1)
- [ ] All authenticated roles can read

## Technical Notes
Eager fetch with projection DTO. `lessons` table populated by solver run (SCHED-03).
