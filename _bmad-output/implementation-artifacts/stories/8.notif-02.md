# Story NOTIF-02 — Timetable Published Notification
**Epic:** Epic 8 — Notifications | **Points:** 4 SP | **Status:** Not Started

## Description
`TIMETABLE_PUBLISHED` WebSocket event to `/topic/tenant/{tenantId}/notifications` on publish; personal queue for targeted user notifications

## Acceptance Criteria
- [ ] `TIMETABLE_PUBLISHED` event published when timetable status changes to PUBLISHED (SCHED-07)
- [ ] Event payload: `{ timetableId, timetableName, termName, publishedAt }`
- [ ] Broadcast to `/topic/tenant/{tenantId}/notifications`
- [ ] Personal notifications also routed to `/queue/user/{userId}/notifications` for targeted alerts
- [ ] Event scoped to tenant (no cross-tenant broadcast)

## Technical Notes
Depends on NOTIF-01. Called from `TimetableService.publish()`.
