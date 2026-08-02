package com.schediflow.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/**
 * Sends transactional email over SMTP with Thymeleaf-rendered HTML (NOTIF-03).
 *
 * <p>Active only when {@code app.mail.enabled=true}. An explicit toggle rather than "is a host
 * configured?": {@code spring.mail.host} has an empty default, and {@code @ConditionalOnProperty}
 * treats an empty value as present, so a host-based condition activates both implementations at
 * once and the context fails on a duplicate {@link EmailService} bean.</p>
 *
 * <p>Sending is {@code @Async} and every failure is swallowed after logging: a bounced notification
 * must never fail — or roll back — the business operation that triggered it.</p>
 */
@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String fromAddress;

    public SmtpEmailService(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            @Value("${app.mail.from:no-reply@schediflow.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromAddress = fromAddress;
    }

    @Override
    @Async
    public void sendInvitation(String toEmail, String inviteUrl) {
        send(toEmail, "You have been invited to SchediFlow", "invitation",
                Map.of("inviteUrl", inviteUrl));
    }

    @Override
    @Async
    public void sendCoverAssigned(String toEmail, String subjectName, String className, String when) {
        send(toEmail, "You have been assigned cover", "cover-assigned",
                Map.of("subjectName", nullSafe(subjectName),
                        "className", nullSafe(className),
                        "when", nullSafe(when)));
    }

    @Override
    @Async
    public void sendDelegationDecision(String toEmail, String type, String status, String reason) {
        send(toEmail, "Your delegation request was " + status.toLowerCase(), "delegation-decision",
                Map.of("type", nullSafe(type),
                        "status", nullSafe(status),
                        "reason", nullSafe(reason)));
    }

    @Override
    @Async
    public void sendTimetablePublished(String toEmail, String timetableName, String termName) {
        send(toEmail, "A new timetable has been published", "timetable-published",
                Map.of("timetableName", nullSafe(timetableName), "termName", nullSafe(termName)));
    }

    private void send(String toEmail, String subject, String template, Map<String, Object> model) {
        try {
            Context context = new Context();
            model.forEach(context::setVariable);
            String html = templateEngine.process("email/" + template, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.debug("Sent '{}' email to {}", template, toEmail);
        } catch (Exception e) {
            // Deliberately swallowed: notification delivery must not fail the operation behind it.
            log.warn("Failed to send '{}' email to {}: {}", template, toEmail, e.getMessage());
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
