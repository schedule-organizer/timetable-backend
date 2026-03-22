# Story SCHED-08 — Move Lesson (Drag & Drop)
**Epic:** Epic 6 — Timetable & Scheduling Engine | **Points:** 3 SP | **Status:** Not Started

## Description
`PATCH /api/v1/lessons/{id}` — move lesson to new period/room via drag-and-drop; run conflict detection; broadcast WebSocket event

## Acceptance Criteria
- [ ] Accepts: new periodId, new roomId (both optional, at least one required)
- [ ] Runs `ConflictDetectionService` on proposed move before committing
- [ ] Conflict detection covers: teacher double-booking, room double-booking, class double-booking, room capacity, forbidden slots
- [ ] Commits move and returns updated lesson with `hasConflict` flag
- [ ] Broadcasts `LESSON_UPDATED` WebSocket event (SCHED-12)
- [ ] Returns 404 if lesson or timetable not found in tenant

## Technical Notes
Optimistic locking on lesson record to prevent concurrent edits. Admin/Mod/Teacher (own lessons) can move.
