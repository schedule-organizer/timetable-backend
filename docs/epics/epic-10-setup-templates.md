# Epic 10 — Setup Templates
**Status:** Not Started | **MVP:** No (Post-MVP) | **Total Points:** 15 SP

## Goal
Provide built-in and custom setup templates that pre-populate bell schedules, constraint defaults, and terminology for common institution types. Reduces onboarding time from hours to minutes.

## Market Driver
Templates directly address adoption friction. The target market persona (non-expert Timetabler) benefits from a guided starting point. Templates are also a sales asset — a demo that starts from a relevant template converts better than a blank canvas.

## Stories
| Story ID | Description | Points | Status |
|---|---|---|---|
| TMPL-01 | Template data model + Flyway migration; `institution_templates` table with embedded configuration JSONB | 3 | Not Started |
| TMPL-02 | Seed: 5 built-in templates — Primary School, Secondary School, High School / Sixth Form, Language School, Vocational Centre | 5 | Not Started |
| TMPL-03 | `POST /api/v1/institutions/apply-template` — apply template settings and bell schedule to tenant; idempotent | 4 | Not Started |
| TMPL-04 | `POST /api/v1/templates` — save current institution configuration as a reusable custom template | 3 | Not Started |

## Notes
Post-MVP epic — can be developed in parallel with later MVP epics (low risk). Low risk to develop alongside Epic 9 sprint.
