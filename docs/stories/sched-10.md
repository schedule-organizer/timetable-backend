# Story SCHED-10 — Atomic Lesson Swap
**Epic:** Epic 6 — Timetable & Scheduling Engine | **Points:** 3 SP | **Status:** Not Started

## Description
`POST /api/v1/lessons/{id}/swap` — atomic swap of two lesson cards with conflict validation on both sides

## Acceptance Criteria
- [ ] Accepts: `targetLessonId` in request body
- [ ] Validates no conflicts for both lessons in their swapped positions before committing
- [ ] Atomically swaps period and room assignments for both lessons
- [ ] Returns both updated lessons with their `hasConflict` flags
- [ ] Returns 400 if swap would create conflicts on either side
- [ ] Broadcasts `LESSON_UPDATED` event for both lessons (SCHED-12)
- [ ] Returns 404 if either lesson not found in tenant

## Technical Notes
Single DB transaction for both updates. Conflict check runs pre-commit on both sides.
