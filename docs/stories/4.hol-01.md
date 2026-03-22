# Story HOL-01 — Holiday Calendar CRUD
**Epic:** Epic 4 — Holiday & Vacation Calendar | **Points:** 3 SP | **Status:** Not Started

## Description
CRUD `/api/v1/holidays` — holiday calendars per academic year; `holiday_calendars` and `holiday_dates` tables; Flyway migration

## Acceptance Criteria
- [ ] CRUD endpoints for `HolidayCalendar` (name, academicYearId, country, region)
- [ ] One calendar per academic year per tenant (enforced)
- [ ] `V00X__create_holiday_calendars.sql` Flyway migration for both tables
- [ ] `holiday_dates` table: date, name, type (PUBLIC_HOLIDAY, SCHOOL_BREAK), calendarId
- [ ] Returns 404 if academicYearId not found in tenant
- [ ] Admin/Mod only for write operations

## Technical Notes
`holiday_calendars` and `holiday_dates` tables with tenant_id FK.
