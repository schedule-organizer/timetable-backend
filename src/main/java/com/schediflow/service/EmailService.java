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

    /** COVER-01: tells a teacher they have been asked to cover a lesson. */
    void sendCoverAssigned(String toEmail, String subjectName, String className, String when);

    /** COVER-04: tells a teacher their delegation request was approved or rejected. */
    void sendDelegationDecision(String toEmail, String type, String status, String reason);

    /** SCHED-07 / NOTIF-02: tells the institution a timetable has gone live. */
    void sendTimetablePublished(String toEmail, String timetableName, String termName);
}
