# Story SCHED-12 — Lesson Updated WebSocket Broadcast
**Epic:** Epic 6 — Timetable & Scheduling Engine | **Points:** 2 SP | **Status:** Not Started

## Description
WebSocket: broadcast `LESSON_UPDATED` to `/topic/timetable/{timetableId}` on every lesson mutation (move, pin, swap, solver result)

## Acceptance Criteria
- [ ] `LESSON_UPDATED` event published to `/topic/timetable/{timetableId}` on: move (SCHED-08), pin (SCHED-09), swap (SCHED-10), solver best solution (SCHED-06)
- [ ] Event payload: `{ lessonId, timetableId, periodId, roomId, teacherId, isPinned, hasConflict, timestamp }`
- [ ] Events scoped to tenant (no cross-tenant leakage via topic auth)
- [ ] WebSocket subscription requires valid JWT

## Technical Notes
Depends on NOTIF-01 (`WebSocketEventPublisher`). Topic-level security via `ChannelInterceptor`.
