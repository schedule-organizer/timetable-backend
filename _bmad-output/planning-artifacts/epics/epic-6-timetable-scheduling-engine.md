# Epic 6 — Timetable & Scheduling Engine
**Status:** Not Started | **MVP:** Yes | **Total Points:** 39 SP

## Goal
The core product capability. Implement timetable lifecycle management, async solver execution via Timefold, real-time progress via WebSocket, and manual lesson manipulation (move, pin, swap) with live conflict detection.

## Market Driver
This is SchediFlow's primary differentiator. Timetablers currently spend 2–4 weeks on manual scheduling. The generator, in combination with manual override capability, directly solves this. The quality score and real-time progress give confidence to non-expert users that the algorithm is working.

## Stories
| Story ID | Description | Points | Status |
|---|---|---|---|
| SCHED-01 | CRUD `/api/v1/timetables` — create/list/get/delete timetables per term; status lifecycle (DRAFT → PUBLISHED → ARCHIVED); Flyway migration | 2 | Not Started |
| SCHED-02 | `GET /api/v1/timetables/{id}/lessons` — full lesson list for grid rendering, includes room, teacher, period, pin status, conflict flag | 3 | Not Started |
| SCHED-03 | `POST /api/v1/engine/run` — async solver job: load problem from DB → build `TimetableSolution` → call `SolverManager.solveAndListen` → return `jobId` immediately; sensitivity and mode parameters | 8 | Not Started |
| SCHED-04 | `GET /api/v1/engine/jobs/{id}` and `GET /api/v1/engine/jobs` — poll job status, quality score, score breakdown, error message; job history per timetable | 2 | Not Started |
| SCHED-05 | `POST /api/v1/engine/jobs/{id}/cancel` — cancel a running solver job; update status to CANCELLED | 2 | Not Started |
| SCHED-06 | WebSocket: `SOLVER_PROGRESS` and `SOLVER_COMPLETE` events on `/topic/solver/{jobId}/progress` and `/topic/solver/{jobId}/complete`; includes percent complete, current score, hard violations | 4 | Not Started |
| SCHED-07 | `POST /api/v1/timetables/{id}/publish` — validate zero hard violations, set status PUBLISHED, record `published_at`; `@Scheduled` job for future-dated publish | 3 | Not Started |
| SCHED-08 | `PATCH /api/v1/lessons/{id}` — move lesson to new period/room via drag-and-drop; run conflict detection; broadcast WebSocket event | 3 | Not Started |
| SCHED-09 | `POST/DELETE /api/v1/lessons/{id}/pin` — pin/unpin a lesson card; pinned cards excluded from solver via `@PinningFilter` | 2 | Not Started |
| SCHED-10 | `POST /api/v1/lessons/{id}/swap` — atomic swap of two lesson cards with conflict validation on both sides | 3 | Not Started |
| SCHED-11 | `ConflictDetectionService` — real-time constraint checks: teacher double-booking, room double-booking, class double-booking, room capacity, forbidden slots | 5 | Not Started |
| SCHED-12 | WebSocket: broadcast `LESSON_UPDATED` to `/topic/timetable/{timetableId}` on every lesson mutation (move, pin, swap, solver result) | 2 | Not Started |

## Notes
In-process JVM solver (no cloud dependency, TD-01). Thread pool isolated via `AsyncConfig`. Solver timeout enforced per subscription tier: Starter 30s, Professional 2 min, Enterprise 10 min. Node-local WebSocket for MVP; Redis pub/sub before scaling beyond 1 backend replica (TD-02).
