# Story CONFIG-02 — Terms CRUD
**Epic:** Epic 3 — Institution Configuration | **Points:** 3 SP | **Status:** Not Started

## Description
CRUD `/api/v1/terms` — terms scoped to academic year; include `ordinal` ordering; Flyway migration

## Acceptance Criteria
- [ ] CRUD endpoints for terms (name, academicYearId, ordinal, startDate, endDate)
- [ ] Terms are scoped to an academic year
- [ ] `ordinal` field enforces display ordering; unique per academic year
- [ ] `V00X__create_terms.sql` Flyway migration included
- [ ] Returns 404 if parent academicYearId not found in tenant
- [ ] Returns 400 if term dates fall outside parent academic year dates

## Technical Notes
`terms` table with academic_year_id FK and tenant_id FK.
