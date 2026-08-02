-- V031__solver_job_scope.sql — record what a partial regeneration was allowed to touch (SCHED-14)

ALTER TABLE solver_jobs ADD COLUMN scope_description VARCHAR(500);
ALTER TABLE solver_jobs ADD COLUMN eligible_lessons  INTEGER;
ALTER TABLE solver_jobs ADD COLUMN frozen_lessons    INTEGER;
