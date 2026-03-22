# Story SCHED-06 — Solver WebSocket Progress Events
**Epic:** Epic 6 — Timetable & Scheduling Engine | **Points:** 4 SP | **Status:** Not Started

## Description
WebSocket: `SOLVER_PROGRESS` and `SOLVER_COMPLETE` events on `/topic/solver/{jobId}/progress` and `/topic/solver/{jobId}/complete`; includes percent complete, current score, hard violations

## Acceptance Criteria
- [ ] `SOLVER_PROGRESS` event published on each new best solution: `{ jobId, percentComplete, hardViolations, softScore, timestamp }`
- [ ] `SOLVER_COMPLETE` event published on termination: `{ jobId, finalScore, scoreBreakdown, lessons[] }`
- [ ] Events published to `/topic/solver/{jobId}/progress` and `/topic/solver/{jobId}/complete`
- [ ] WebSocket connection requires valid JWT (STOMP CONNECT header)
- [ ] Events scoped to tenant (no cross-tenant leakage)

## Technical Notes
Depends on NOTIF-01 (`WebSocketEventPublisher`). Solver listener calls publisher on each `BestSolutionChangedEvent`.
