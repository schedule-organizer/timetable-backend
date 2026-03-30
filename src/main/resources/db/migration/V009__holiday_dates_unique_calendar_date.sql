-- HOL-02 — one holiday row per calendar per calendar date (idempotent public import)

CREATE UNIQUE INDEX uq_holiday_dates_calendar_date ON holiday_dates(holiday_calendar_id, date);
