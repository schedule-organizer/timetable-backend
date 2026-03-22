# Story RES-07 — Teaching Groups CRUD
**Epic:** Epic 5 — Resource Management | **Points:** 4 SP | **Status:** Not Started

## Description
CRUD `/api/v1/teaching-groups` — groups linking teachers, subjects, and classes; support SET, MIXED, OPTION_BLOCK types; Flyway migration

## Acceptance Criteria
- [ ] CRUD for teaching groups (name, type, teacherId, subjectId, classIds[])
- [ ] Types: SET (one class), MIXED (multiple classes combined), OPTION_BLOCK (managed by option block)
- [ ] `V00X__create_teaching_groups.sql` Flyway migration included
- [ ] Returns 404 if teacher, subject, or any classId not found in tenant
- [ ] Prevents duplicate teacher+subject+class combinations
- [ ] Soft delete — groups with lesson assignments cannot be hard deleted

## Technical Notes
`teaching_groups` and `teaching_group_classes` junction tables.
