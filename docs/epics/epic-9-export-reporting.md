# Epic 9 — Export & Reporting
**Status:** Not Started | **MVP:** Yes | **Total Points:** 25 SP

## Goal
Enable timetable export in PDF, CSV, and iCal formats, plus utilization and audit reports for administrators.

## Market Driver
Schools need to distribute timetables to students, parents, and teachers via multiple channels. PDF export supports traditional physical distribution. iCal export enables integration with personal calendars, directly addressing the student and teacher visibility gap. The audit log meets governance and accountability expectations of institutional buyers.

## Stories
| Story ID | Description | Points | Status |
|---|---|---|---|
| EXPORT-01 | `GET /api/v1/timetables/{id}/export/pdf` — generate printable PDF timetable grid; class, teacher, and room views; library TBD (TD-05: Flying Saucer vs headless Chrome) | 5 | Not Started |
| EXPORT-02 | `GET /api/v1/timetables/{id}/export/csv` — CSV export of all lessons with teacher, room, period columns; Admin/Mod only | 3 | Not Started |
| EXPORT-03 | `GET /api/v1/timetables/{id}/export/ical?userId=...` — personal `.ics` file scoped to a teacher or student's lessons | 4 | Not Started |
| EXPORT-05 | Teacher utilization report: periods assigned vs capacity, gap count, subject distribution; JSON response | 4 | Not Started |
| EXPORT-06 | Room utilization report: occupancy percentage per period, by room type; JSON response | 3 | Not Started |
| EXPORT-07 | Subject coverage report: actual vs required periods per class; flags under/over-scheduled subjects | 3 | Not Started |
| EXPORT-08 | `GET /api/v1/audit-log` — paginated audit log with filters (actor, entity type, date range); `@Audited` AOP auto-populates log from annotated service methods | 3 | Not Started |

## Notes
Open technical decision TD-04: `StorageService` interface defined now; local volume for MVP, S3 post-MVP. TD-05: Flying Saucer for simple grid; headless Chrome if pixel-perfect required.
