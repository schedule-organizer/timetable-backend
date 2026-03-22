# Story CONFIG-01 — Academic Years CRUD
**Epic:** Epic 3 — Institution Configuration | **Points:** 3 SP | **Status:** Not Started

## Description
CRUD `/api/v1/academic-years` — create/read/update/delete academic years; only one can be `is_active`; Flyway migration

## Acceptance Criteria
- [ ] CRUD endpoints for academic years (name, startDate, endDate, isActive)
- [ ] Only one academic year can be `is_active=true` per tenant at a time (enforced at service layer)
- [ ] Setting a year active automatically deactivates the previously active year
- [ ] `V00X__create_academic_years.sql` Flyway migration included
- [ ] Returns 400 if startDate >= endDate
- [ ] Admin/Mod only for write operations; all roles can read

## Technical Notes
`academic_years` table with tenant_id FK. Hibernate `@Filter` applied.
