# Story EXPORT-01 — PDF Timetable Export
**Epic:** Epic 9 — Export & Reporting | **Points:** 5 SP | **Status:** Not Started

## Description
`GET /api/v1/timetables/{id}/export/pdf` — generate printable PDF timetable grid; class, teacher, and room views; library TBD (TD-05: Flying Saucer vs headless Chrome)

## Acceptance Criteria
- [ ] Generates PDF timetable grid for: class view, teacher view, room view (query param `view=CLASS|TEACHER|ROOM`)
- [ ] PDF includes: school name, timetable name, term dates, grid of periods × days with lesson details
- [ ] Returns PDF as `Content-Type: application/pdf` with `Content-Disposition: attachment`
- [ ] Returns 404 if timetable not found in tenant
- [ ] PDF generation completes in < 10 seconds for typical school (< 500 lessons)
- [ ] Admin/Mod only

## Technical Notes
TD-05 decision: Flying Saucer (`xhtmlrenderer`) for MVP. If pixel-perfect required, switch to headless Chrome (Playwright). HTML template → PDF pipeline.
