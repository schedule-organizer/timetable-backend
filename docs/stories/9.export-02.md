# Story EXPORT-02 — CSV Timetable Export
**Epic:** Epic 9 — Export & Reporting | **Points:** 3 SP | **Status:** Not Started

## Description
`GET /api/v1/timetables/{id}/export/csv` — CSV export of all lessons with teacher, room, period columns; Admin/Mod only

## Acceptance Criteria
- [ ] Returns CSV with columns: lessonId, subject, teacher, room, class, dayOfWeek, periodName, startTime, endTime
- [ ] Returns as `Content-Type: text/csv` with `Content-Disposition: attachment; filename="timetable-{id}.csv"`
- [ ] Returns 404 if timetable not found in tenant
- [ ] Admin/Mod only; Teachers get 403
- [ ] Sorted by dayOfWeek, then periodName
- [ ] UTF-8 encoded with BOM for Excel compatibility

## Technical Notes
Use Apache Commons CSV. Stream response to avoid loading all lessons into memory for large timetables.
