-- V026__timetable_lifecycle.sql — constrain the timetable status lifecycle (SCHED-01)
--
-- V011 created `timetables` with a free-text status defaulting to 'DRAFT'. SCHED-01 defines the
-- lifecycle properly, so the column is now restricted to the three legal states. Transition rules
-- (no going backwards, one PUBLISHED per term) are enforced in TimetableService, which can produce
-- a useful message; this CHECK only guarantees no third-party writer invents a fourth state.

UPDATE timetables SET status = 'DRAFT' WHERE status NOT IN ('DRAFT', 'PUBLISHED', 'ARCHIVED');

ALTER TABLE timetables
    ADD CONSTRAINT chk_timetables_status
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'));

CREATE INDEX idx_timetables_term_status ON timetables(tenant_id, term_id, status);
