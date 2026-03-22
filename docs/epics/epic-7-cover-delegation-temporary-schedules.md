# Epic 7 — Cover, Delegation & Temporary Schedules
**Status:** Not Started | **MVP:** Yes | **Total Points:** 25 SP

## Goal
Day-to-day operational scheduling: assign cover teachers for absent staff, handle teacher-to-teacher swap/handover requests, and manage exceptional-week temporary schedules.

## Market Driver
The MRD identifies absence management as the "Absence Management Gap" — a distinct problem from initial timetable creation. Cover is currently assigned via phone calls and WhatsApp. Digitising this workflow reduces administrative burden and creates an audit trail, which is a key selling point for institutional buyers.

## Stories
| Story ID | Description | Points | Status |
|---|---|---|---|
| COVER-01 | `POST /api/v1/cover` — assign cover teacher to a lesson; validate qualification for subject and no timetable conflict; Flyway migration | 4 | Not Started |
| COVER-02 | `GET /api/v1/cover/candidates?lessonId=...` — return qualified, available teachers sorted by workload gap; respects forbidden slots | 4 | Not Started |
| COVER-03 | `POST /api/v1/delegation` — teacher submits SWAP or HANDOVER request for one or more lessons; Flyway migration | 3 | Not Started |
| COVER-04 | `PATCH /api/v1/delegation/{id}` — moderator approves or rejects delegation request; on approval, atomically reassigns lessons | 3 | Not Started |
| COVER-05 | CRUD `/api/v1/temporary-schedules` — create named temporary schedule overlaying a base timetable for a date range; Flyway migration | 5 | Not Started |
| COVER-06 | `@Scheduled` job: auto-expire temporary schedules at `end_date`; revert lessons to base timetable | 3 | Not Started |
| COVER-07 | WebSocket: `COVER_ASSIGNED` event to `/topic/tenant/{tenantId}/notifications`; `DELEGATION_UPDATE` event to requesting and receiving teacher queues | 3 | Not Started |

## Notes
Prerequisites: Epics 1–6. Cover assignment validates against published timetable data from Epic 6.
