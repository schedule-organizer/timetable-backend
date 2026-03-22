# Story RES-11 — CSV Bulk Import
**Epic:** Epic 5 — Resource Management | **Points:** 8 SP | **Status:** Not Started

## Description
CSV bulk import endpoint — parse and upsert Rooms, Classes, Teachers via multipart upload; return row-level error report

## Acceptance Criteria
- [ ] `POST /api/v1/import/{entityType}` accepts multipart CSV file (entityType: rooms, classes, teachers)
- [ ] Parses CSV, validates each row, upserts valid rows (insert or update by name)
- [ ] Returns row-level error report: `{ row, field, error }` for each failure
- [ ] Partial success: valid rows imported even if some rows fail
- [ ] Max file size: 5MB; max rows: 1000
- [ ] Returns 400 for malformed CSV or unrecognised entityType
- [ ] Admin/Mod only

## Technical Notes
Use Apache Commons CSV or OpenCSV. Process in a single transaction per valid row (not all-or-nothing). Return summary: imported, updated, skipped, errors.
