# Story EXPORT-06 — Room Utilization Report
**Epic:** Epic 9 — Export & Reporting | **Points:** 3 SP | **Status:** Not Started

## Description
Room utilization report: occupancy percentage per period, by room type; JSON response

## Acceptance Criteria
- [ ] `GET /api/v1/timetables/{id}/reports/room-utilization`
- [ ] Returns per-room: roomName, roomType, occupancyByPeriod (period → occupancyPct), avgOccupancy
- [ ] Also returns aggregate by room type: avgOccupancyByType
- [ ] Returns 404 if timetable not found in tenant
- [ ] Admin/Mod only
- [ ] Occupancy = (assigned lessons / total periods in cycle) * 100

## Technical Notes
Read-only aggregation. Total periods = days × periods per day from bell schedule.
