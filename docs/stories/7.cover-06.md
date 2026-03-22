# Story COVER-06 — Auto-Expire Temporary Schedules
**Epic:** Epic 7 — Cover, Delegation & Temporary Schedules | **Points:** 3 SP | **Status:** Not Started

## Description
`@Scheduled` job: auto-expire temporary schedules at `end_date`; revert lessons to base timetable

## Acceptance Criteria
- [ ] `@Scheduled` job runs daily (or at configurable interval)
- [ ] Identifies temporary schedules where `end_date` < today
- [ ] Sets status to EXPIRED
- [ ] Clears temporary lesson overrides (base timetable lessons resume automatically)
- [ ] Idempotent: safe to run multiple times on same day
- [ ] Logs expiry actions for audit

## Technical Notes
Use `@Scheduled(cron = "0 0 1 * * *")` (1 AM daily). No notification required for expiry.
