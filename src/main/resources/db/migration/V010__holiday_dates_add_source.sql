-- Add source column to holiday_dates to track whether a date was imported or added manually.
-- Existing rows default to 'MANUAL' for backwards compatibility.
ALTER TABLE holiday_dates
    ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'MANUAL';
