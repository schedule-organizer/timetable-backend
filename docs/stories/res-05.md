# Story RES-05 — Teacher Qualifications
**Epic:** Epic 5 — Resource Management | **Points:** 2 SP | **Status:** Not Started

## Description
`POST/DELETE /api/v1/teachers/{id}/qualifications` — add/remove subject qualifications with optional periods-per-cycle allocation

## Acceptance Criteria
- [ ] `POST` adds a qualification (subjectId, periodsPerCycle optional)
- [ ] `DELETE /api/v1/teachers/{id}/qualifications/{qualId}` removes a qualification
- [ ] Returns 404 if teacher or subject not found in tenant
- [ ] Returns 409 if qualification for that subject already exists
- [ ] `GET /api/v1/teachers/{id}/qualifications` lists all qualifications for a teacher
- [ ] Solver uses qualifications to restrict teacher-subject assignments

## Technical Notes
`teacher_qualifications` junction table.
