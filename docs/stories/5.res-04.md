# Story RES-04 — Teacher Profiles CRUD
**Epic:** Epic 5 — Resource Management | **Points:** 3 SP | **Status:** Not Started

## Description
CRUD `/api/v1/teachers` — teacher profiles linked to user accounts; workload caps, max consecutive periods; Flyway migration

## Acceptance Criteria
- [ ] CRUD for teacher profiles (userId, displayName, maxPeriodsPerDay, maxConsecutivePeriods, workloadCap)
- [ ] Each teacher profile linked to a `User` record (FK)
- [ ] `V00X__create_teachers.sql` Flyway migration included
- [ ] Returns 404 if userId not found in tenant
- [ ] Returns 409 if user already has a teacher profile
- [ ] Soft delete — teachers with active assignments cannot be hard deleted

## Technical Notes
`teachers` table with user_id FK and tenant_id FK.
