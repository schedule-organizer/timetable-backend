# Story RES-10 — Teacher Availability View
**Epic:** Epic 5 — Resource Management | **Points:** 2 SP | **Status:** Not Started

## Description
`GET /api/v1/teachers/{id}/availability` — returns forbidden slots + soft preferences for a teacher

## Acceptance Criteria
- [ ] Returns combined view: forbidden slots (hard unavailability) + soft preferences
- [ ] Soft preferences: PREFERRED_FREE, PREFERRED_TEACHING (advisory only for solver)
- [ ] Returns 404 if teacher not found in tenant
- [ ] Response structured as weekly grid (dayOfWeek × periodId → availability status)
- [ ] All authenticated roles can read own availability; Admin/Mod can read any teacher's

## Technical Notes
Joins `forbidden_slots` and `teacher_preferences` (if implemented). Teachers can only read their own via role check.
