# Story SCHED-09 — Pin/Unpin Lesson
**Epic:** Epic 6 — Timetable & Scheduling Engine | **Points:** 2 SP | **Status:** Not Started

## Description
`POST/DELETE /api/v1/lessons/{id}/pin` — pin/unpin a lesson card; pinned cards excluded from solver via `@PinningFilter`

## Acceptance Criteria
- [ ] `POST /api/v1/lessons/{id}/pin` sets `isPinned=true`
- [ ] `DELETE /api/v1/lessons/{id}/pin` sets `isPinned=false`
- [ ] Pinned lessons excluded from solver re-scheduling via Timefold `@PinningFilter`
- [ ] Returns 404 if lesson not found in tenant
- [ ] Broadcasts `LESSON_UPDATED` WebSocket event (SCHED-12)
- [ ] All roles can pin/unpin (teachers can pin their own lessons)

## Technical Notes
Timefold `@PinningFilter` reads `isPinned` flag. No conflict check needed on pin/unpin alone.
