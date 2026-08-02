-- V027__expand_lessons.sql — room, pinning and optimistic locking on lessons (SCHED-11 / 02 / 08 / 09)
--
-- V015 created a minimal `lessons` table for HOL-07's holiday checks. Epic 6 needs three more
-- columns. They land here, with SCHED-11, because conflict detection is the first consumer:
-- room double-booking and room-capacity checks are impossible without `room_id`.
--
--   room_id  — nullable: the solver may leave a lesson unroomed, and not every subject needs one
--   is_pinned — SCHED-09; the solver must not move a pinned lesson
--   version  — SCHED-08 uses optimistic locking to stop concurrent drag-and-drop edits colliding

ALTER TABLE lessons ADD COLUMN room_id BIGINT REFERENCES rooms(id) ON DELETE SET NULL;
ALTER TABLE lessons ADD COLUMN is_pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE lessons ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_lessons_slot ON lessons(tenant_id, timetable_id, scheduled_date, schedule_period_id);
CREATE INDEX idx_lessons_room ON lessons(room_id);
