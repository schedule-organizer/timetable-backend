# Story HOL-04 — List Holiday Dates by Academic Year
**Epic:** Epic 4 — Holiday & Vacation Calendar | **Points:** 1 SP | **Status:** Not Started

## Description
`GET /api/v1/holidays?academicYearId=...` — return all dates (imported + manual) for a given academic year

## Acceptance Criteria
- [ ] Returns all holiday dates (imported + manual) for the given academicYearId
- [ ] Response sorted by date ascending
- [ ] Returns 404 if academicYearId not found in tenant
- [ ] All authenticated roles can read
- [ ] Response includes: date, name, type, source (IMPORTED/MANUAL)

## Technical Notes
Join `holiday_calendars` → `holiday_dates` filtered by academicYearId.
