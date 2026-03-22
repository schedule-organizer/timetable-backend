# Story RES-02 — Subjects CRUD
**Epic:** Epic 5 — Resource Management | **Points:** 3 SP | **Status:** Not Started

## Description
CRUD `/api/v1/subjects` — difficulty level, color, required room type, spread rules, max per day; Flyway migration

## Acceptance Criteria
- [ ] CRUD for subjects (name, code, color, difficultyLevel, requiredRoomType, maxPerDay, spreadPattern)
- [ ] `V00X__create_subjects.sql` Flyway migration included
- [ ] Color stored as hex string (#RRGGBB), validated on input
- [ ] Returns 409 if subject code already exists in tenant
- [ ] Soft delete — subjects with teaching assignments cannot be hard deleted
- [ ] All authenticated roles can read

## Technical Notes
`subjects` table with tenant_id FK.
