package com.schediflow.service;

/**
 * Contract for sending transactional emails.
 * Production implementation (SmtpEmailService) will be added in NOTIF-03.
 */
public interface EmailService {

    /**
     * Sends a teacher invitation email containing the registration link.
     *
     * @param toEmail   recipient email address
     * @param inviteUrl full URL the recipient must visit to complete registration
     */
    void sendInvitation(String toEmail, String inviteUrl);
}
