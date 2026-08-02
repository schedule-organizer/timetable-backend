-- V029__timetable_publishing.sql — publication timestamps and future-dated publishing (SCHED-07)

ALTER TABLE timetables ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE timetables ADD COLUMN publish_at   TIMESTAMP WITH TIME ZONE;

-- The scheduled sweep looks for DRAFT rows whose publish_at has come due.
CREATE INDEX idx_timetables_publish_at ON timetables(status, publish_at);
