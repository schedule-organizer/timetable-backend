package com.schediflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Logs instead of sending. Active unless {@code app.mail.enabled=true}, which keeps tests and
 * offline development from needing a mail server (NOTIF-03).
 */
@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class StubEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(StubEmailService.class);

    @Override
    public void sendInvitation(String toEmail, String inviteUrl) {
        log.info("[EMAIL] Invite link for {}: {}", toEmail, inviteUrl);
    }

    @Override
    public void sendCoverAssigned(String toEmail, String subjectName, String className, String when) {
        log.info("[EMAIL] Cover for {}: {} with {} on {}", toEmail, subjectName, className, when);
    }

    @Override
    public void sendDelegationDecision(String toEmail, String type, String status, String reason) {
        log.info("[EMAIL] Delegation {} {} for {} ({})", type, status, toEmail, reason);
    }

    @Override
    public void sendTimetablePublished(String toEmail, String timetableName, String termName) {
        log.info("[EMAIL] Timetable '{}' ({}) published, notifying {}", timetableName, termName, toEmail);
    }
}
