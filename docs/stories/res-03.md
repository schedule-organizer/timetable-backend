# Story RES-03 — School Classes CRUD
**Epic:** Epic 5 — Resource Management | **Points:** 3 SP | **Status:** Not Started

## Description
CRUD `/api/v1/classes` — school classes with year level, homeroom, capacity; Flyway migration

## Acceptance Criteria
- [ ] CRUD for school classes (name, yearLevel, homeroomId, capacity)
- [ ] `V00X__create_classes.sql` Flyway migration included
- [ ] `homeroomId` references a room (FK, nullable)
- [ ] Returns 409 if class name already exists in tenant
- [ ] Soft delete — classes with active timetable assignments cannot be hard deleted
- [ ] All authenticated roles can read

## Technical Notes
`classes` table with tenant_id FK. Note: "classes" is reserved in some DBs — use `school_classes` table name.
