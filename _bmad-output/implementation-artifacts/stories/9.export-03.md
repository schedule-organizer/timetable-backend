# Story EXPORT-03 — iCal Personal Schedule Export
**Epic:** Epic 9 — Export & Reporting | **Points:** 4 SP | **Status:** Not Started

## Description
`GET /api/v1/timetables/{id}/export/ical?userId=...` — personal `.ics` file scoped to a teacher or student's lessons

## Acceptance Criteria
- [ ] Generates `.ics` file scoped to a specific teacher's or class's lessons
- [ ] `userId` param: teacher gets their own lessons; Admin/Mod can export for any user
- [ ] Each lesson becomes a VEVENT with: summary (subject + class), location (room), dtstart/dtend (calculated from term dates + period times), rrule (weekly recurrence)
- [ ] Returns as `Content-Type: text/calendar`
- [ ] Excludes holiday dates (no VEVENT on holiday dates)
- [ ] Returns 404 if timetable or userId not found in tenant

## Technical Notes
Use `ical4j` library. RRULE calculated from term start/end + day of week.
