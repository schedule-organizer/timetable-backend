# Story HOL-03 — Manual Holiday Date CRUD
**Epic:** Epic 4 — Holiday & Vacation Calendar | **Points:** 2 SP | **Status:** Not Started

## Description
Manual holiday date CRUD — add, edit, delete individual dates within a calendar

## Acceptance Criteria
- [ ] `POST /api/v1/holidays/{calendarId}/dates` — add a single holiday date (date, name, type)
- [ ] `PUT /api/v1/holidays/{calendarId}/dates/{dateId}` — edit name or type
- [ ] `DELETE /api/v1/holidays/{calendarId}/dates/{dateId}` — remove a date
- [ ] Returns 404 if calendarId or dateId not found in tenant
- [ ] Duplicate dates within same calendar rejected (400)
- [ ] Admin/Mod only

## Technical Notes
Manual dates coexist with imported dates; type distinguishes source.
