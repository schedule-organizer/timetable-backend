# Story EXPORT-07 — Subject Coverage Report
**Epic:** Epic 9 — Export & Reporting | **Points:** 3 SP | **Status:** Not Started

## Description
Subject coverage report: actual vs required periods per class; flags under/over-scheduled subjects

## Acceptance Criteria
- [ ] `GET /api/v1/timetables/{id}/reports/subject-coverage`
- [ ] Returns per class × subject: required (from RES-06), actual (from timetable lessons), variance, status (ON_TARGET/UNDER/OVER)
- [ ] UNDER: actual < required; OVER: actual > required
- [ ] Summary: totalUnder, totalOver, totalOnTarget
- [ ] Returns 404 if timetable not found in tenant
- [ ] All authenticated roles can read

## Technical Notes
Joins `class_subject_hours` (requirements) with `lessons` (actuals) grouped by class + subject.
