-- RES-06: referential integrity and spread_pattern defaults/constraints for class_subject_hours

DELETE FROM class_subject_hours
WHERE class_id NOT IN (SELECT id FROM school_classes);

UPDATE class_subject_hours SET spread_pattern = 'ANY' WHERE spread_pattern IS NULL;

ALTER TABLE class_subject_hours
    ALTER COLUMN spread_pattern SET DEFAULT 'ANY';

ALTER TABLE class_subject_hours
    ALTER COLUMN spread_pattern SET NOT NULL;

ALTER TABLE class_subject_hours
    ADD CONSTRAINT fk_csh_school_class
    FOREIGN KEY (class_id) REFERENCES school_classes(id) ON DELETE CASCADE;

ALTER TABLE class_subject_hours
    ADD CONSTRAINT chk_csh_spread_pattern
    CHECK (spread_pattern IN ('SPREAD', 'CLUSTER', 'ANY'));
