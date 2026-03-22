# Epic 4 — Holiday & Vacation Calendar
**Status:** Not Started | **MVP:** Yes | **Total Points:** 17 SP

## Goal
Maintain a per-academic-year holiday calendar that feeds into the scheduling engine as globally forbidden slots. Supports both public holiday import and manual overrides.

## Market Driver
Holiday-aware scheduling is a baseline expectation. Schools cannot publish a timetable that schedules lessons on public holidays. The import-from-feed feature reduces administrator setup time, directly addressing the "2–4 weeks of manual work" problem identified in the MRD.

## Stories
| Story ID | Description | Points | Status |
|---|---|---|---|
| HOL-01 | CRUD `/api/v1/holidays` — holiday calendars per academic year; `holiday_calendars` and `holiday_dates` tables; Flyway migration | 3 | Not Started |
| HOL-02 | `POST /api/v1/holidays/import` — fetch public holiday dates from external feed (Calendarific free tier, TD-06) by country/region; persist as `holiday_dates`; idempotent on re-import | 5 | Not Started |
| HOL-03 | Manual holiday date CRUD — add, edit, delete individual dates within a calendar | 2 | Not Started |
| HOL-04 | `GET /api/v1/holidays?academicYearId=...` — return all dates (imported + manual) for a given academic year | 1 | Not Started |
| HOL-05 | Solver integration: `HolidayService` loads holiday dates and converts them to globally forbidden period slots before each solver run | 3 | Not Started |
| HOL-07 | Conflict detection: `ConflictDetectionService` warns when a published lesson falls on a newly added holiday date | 3 | Not Started |

## Notes
Open technical decision TD-06: Calendarific free tier chosen as holiday data source for MVP.
