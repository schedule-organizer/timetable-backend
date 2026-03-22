# Story COVER-07 — Cover & Delegation WebSocket Events
**Epic:** Epic 7 — Cover, Delegation & Temporary Schedules | **Points:** 3 SP | **Status:** Not Started

## Description
WebSocket: `COVER_ASSIGNED` event to `/topic/tenant/{tenantId}/notifications`; `DELEGATION_UPDATE` event to requesting and receiving teacher queues

## Acceptance Criteria
- [ ] `COVER_ASSIGNED` event: `{ lessonId, coverTeacherId, originalTeacherId, assignedAt }` → `/topic/tenant/{tenantId}/notifications`
- [ ] `DELEGATION_UPDATE` event: `{ requestId, type, status, lessonIds[] }` → personal queues for both requesting and target teachers
- [ ] Personal queue format: `/queue/user/{userId}/notifications`
- [ ] Events scoped to tenant
- [ ] WebSocket subscription requires valid JWT

## Technical Notes
Depends on NOTIF-01 (`WebSocketEventPublisher`). Personal queues via STOMP user destinations.
