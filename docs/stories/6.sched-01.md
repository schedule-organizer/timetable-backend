# Story SCHED-01 — Timetable CRUD & Lifecycle
**Epic:** Epic 6 — Timetable & Scheduling Engine | **Points:** 2 SP | **Status:** Not Started

## Description
CRUD `/api/v1/timetables` — create/list/get/delete timetables per term; status lifecycle (DRAFT → PUBLISHED → ARCHIVED); Flyway migration

## Acceptance Criteria
- [ ] CRUD for timetables (name, termId, status)
- [ ] Status lifecycle: DRAFT → PUBLISHED → ARCHIVED (no backwards transitions)
- [ ] `V00X__create_timetables.sql` Flyway migration included
- [ ] Only one PUBLISHED timetable per term per tenant (enforced)
- [ ] Listing supports filter by termId, status
- [ ] Hard delete only allowed on DRAFT timetables

## Technical Notes
`timetables` table with term_id FK and tenant_id FK.
