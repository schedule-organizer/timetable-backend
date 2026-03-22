# Story HOL-02 — Public Holiday Import
**Epic:** Epic 4 — Holiday & Vacation Calendar | **Points:** 5 SP | **Status:** Not Started

## Description
`POST /api/v1/holidays/import` — fetch public holiday dates from external feed (Calendarific free tier, TD-06) by country/region; persist as `holiday_dates`; idempotent on re-import

## Acceptance Criteria
- [ ] Accepts: calendarId, country code, region (optional), year
- [ ] Fetches holidays from Calendarific API
- [ ] Persists returned dates as `holiday_dates` with type `PUBLIC_HOLIDAY`
- [ ] Idempotent: re-importing same country/year updates existing records, does not duplicate
- [ ] Returns count of imported/updated/skipped dates
- [ ] Returns 502 if Calendarific API is unreachable (with user-friendly error)
- [ ] Calendarific API key configured via environment variable

## Technical Notes
HTTP client with timeout (5s). Wrap Calendarific client in `HolidayFeedClient` interface for testability.
