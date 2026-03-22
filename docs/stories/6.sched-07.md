# Story SCHED-07 — Publish Timetable
**Epic:** Epic 6 — Timetable & Scheduling Engine | **Points:** 3 SP | **Status:** Not Started

## Description
`POST /api/v1/timetables/{id}/publish` — validate zero hard violations, set status PUBLISHED, record `published_at`; `@Scheduled` job for future-dated publish

## Acceptance Criteria
- [ ] Validates timetable has zero hard constraint violations before publishing
- [ ] Returns 400 with violation details if hard violations exist
- [ ] Sets status to PUBLISHED, records `published_at` timestamp
- [ ] Triggers `TIMETABLE_PUBLISHED` WebSocket event (NOTIF-02)
- [ ] Supports `publishAt` parameter for future-dated publishing (stored, executed by `@Scheduled` job)
- [ ] Only one PUBLISHED timetable allowed per term (existing published timetable auto-archived)

## Technical Notes
`@Scheduled` job checks for pending future-dated publishes every minute.
