# Story RES-06 — Class Subject Hours Matrix
**Epic:** Epic 5 — Resource Management | **Points:** 3 SP | **Status:** Not Started

## Description
`GET/PUT /api/v1/classes/{id}/subject-hours` — weekly hours matrix (class × subject → periods per cycle + spread pattern)

## Acceptance Criteria
- [ ] `GET` returns list of subject-hours allocations for the class (subjectId, periodsPerCycle, spreadPattern)
- [ ] `PUT` replaces the full subject-hours allocation for the class
- [ ] spreadPattern options: SPREAD (no consecutive), CLUSTER (allow consecutive), ANY
- [ ] Returns 404 if class not found in tenant
- [ ] Total periods per cycle validated against bell schedule capacity
- [ ] All allocations replaced atomically on PUT

## Technical Notes
`class_subject_hours` table. Used by solver as the requirement input.
