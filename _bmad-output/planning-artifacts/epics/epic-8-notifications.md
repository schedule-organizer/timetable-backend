# Epic 8 — Notifications
**Status:** Not Started | **MVP:** Yes | **Total Points:** 12 SP

## Goal
Deliver real-time WebSocket events and transactional emails for key lifecycle events across the platform.

## Market Driver
Teachers and administrators expect immediate notification of changes that affect their schedules. This is standard in modern SaaS and directly supports SchediFlow's "teacher visibility" differentiator versus the current reality of printed timetable sheets posted on walls.

## Stories
| Story ID | Description | Points | Status |
|---|---|---|---|
| NOTIF-01 | `WebSocketEventPublisher` — typed event publisher for all STOMP topics; `WebSocketConfig` with SockJS fallback at `ws://{host}/ws` | 4 | Not Started |
| NOTIF-02 | `TIMETABLE_PUBLISHED` WebSocket event to `/topic/tenant/{tenantId}/notifications` on publish; personal queue for targeted user notifications | 4 | Not Started |
| NOTIF-03 | `SmtpEmailService` (via MailHog for MVP): transactional emails for teacher invite, cover assignment, delegation status change, and timetable published | 4 | Not Started |

## Notes
Post-MVP: Replace `SmtpEmailService` with `SesEmailService` (AWS SES). Add Twilio OTP for 2FA. `StorageService` swaps from local volume to S3 via interface change only.
