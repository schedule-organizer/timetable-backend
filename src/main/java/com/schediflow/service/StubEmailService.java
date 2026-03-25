package com.schediflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * No-op email service that logs the invitation link instead of sending a real email.
 * Replaces SmtpEmailService until NOTIF-03 implements real SMTP delivery.
 */
@Service
public class StubEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(StubEmailService.class);

    @Override
    public void sendInvitation(String toEmail, String inviteUrl) {
        log.info("[EMAIL] Invite link for {}: {}", toEmail, inviteUrl);
    }
}
