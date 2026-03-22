# Story EXPORT-05 — Teacher Utilization Report
**Epic:** Epic 9 — Export & Reporting | **Points:** 4 SP | **Status:** Not Started

## Description
Teacher utilization report: periods assigned vs capacity, gap count, subject distribution; JSON response

## Acceptance Criteria
- [ ] `GET /api/v1/timetables/{id}/reports/teacher-utilization`
- [ ] Returns per-teacher: periodsAssigned, workloadCap, utilizationPct, gapCount (free periods between lessons), subjectDistribution[]
- [ ] Sorted by utilizationPct descending
- [ ] Returns 404 if timetable not found in tenant
- [ ] Admin/Mod only
- [ ] Response also includes summary: avgUtilization, overloadedCount (> 100%), underutilizedCount (< 70%)

## Technical Notes
Read-only aggregation query. Gap count = non-lesson periods between first and last assigned period per day.
