# Story NOTIF-03 — Transactional Email Service
**Epic:** Epic 8 — Notifications | **Points:** 4 SP | **Status:** Not Started

## Description
`SmtpEmailService` (via MailHog for MVP): transactional emails for teacher invite, cover assignment, delegation status change, and timetable published

## Acceptance Criteria
- [ ] `SmtpEmailService` implements `EmailService` interface (for future SES swap)
- [ ] Sends emails for: teacher invite (AUTH-05), cover assignment (COVER-01), delegation status change (COVER-04), timetable published (SCHED-07)
- [ ] Email templates use Thymeleaf for HTML rendering
- [ ] MailHog configured as SMTP server in `docker-compose.yml` (dev environment)
- [ ] Failed sends logged and do not cause transaction rollback
- [ ] `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM` configured via environment variables

## Technical Notes
`@Async` email sending to avoid blocking request thread. Thymeleaf templates in `resources/templates/email/`.
