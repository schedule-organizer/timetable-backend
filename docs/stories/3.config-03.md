# Story CONFIG-03 — Bell Schedules & Periods CRUD
**Epic:** Epic 3 — Institution Configuration | **Points:** 4 SP | **Status:** Not Started

## Description
CRUD `/api/v1/bell-schedules` + nested periods — bell schedule with named periods, times, break/lunch flags; one default per tenant; Flyway migration

## Acceptance Criteria
- [ ] CRUD for bell schedules (name, isDefault) and nested periods (name, startTime, endTime, isBreak, isLunch, ordinal)
- [ ] Only one bell schedule can be `isDefault=true` per tenant
- [ ] Periods are created/updated/deleted via nested operations on the schedule endpoint
- [ ] `V00X__create_bell_schedules.sql` Flyway migration included
- [ ] Periods validated: no overlapping times within same schedule
- [ ] Returns 400 if deleting the only default schedule

## Technical Notes
`bell_schedules` and `schedule_periods` tables with tenant_id FK.
