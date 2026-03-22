# Story COVER-02 — Cover Candidate Suggestions
**Epic:** Epic 7 — Cover, Delegation & Temporary Schedules | **Points:** 4 SP | **Status:** Not Started

## Description
`GET /api/v1/cover/candidates?lessonId=...` — return qualified, available teachers sorted by workload gap; respects forbidden slots

## Acceptance Criteria
- [ ] Returns list of teachers qualified for the lesson's subject
- [ ] Filters out teachers with timetable conflicts in the lesson's period
- [ ] Filters out teachers with forbidden slots for the lesson's period
- [ ] Sorts by workload gap (available capacity - current assignments) descending
- [ ] Returns: teacherId, displayName, qualifications, currentWorkload, workloadCap, workloadGap
- [ ] Returns 404 if lessonId not found in tenant
- [ ] Returns empty list if no qualified available teachers exist

## Technical Notes
Read-only query. Workload gap = workloadCap - count(assigned lessons this cycle).
