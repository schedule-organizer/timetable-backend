package com.schediflow.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock TemplateEngine templateEngine;

    SmtpEmailService service;

    @BeforeEach
    void setUp() {
        service = new SmtpEmailService(mailSender, templateEngine, "no-reply@test.edu");
        lenient().when(templateEngine.process(anyString(), any(IContext.class))).thenReturn("<html/>");
        lenient().when(mailSender.createMimeMessage())
                .thenReturn(new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null));
    }

    @Test
    void sendsInvitationThroughTheInvitationTemplate() {
        service.sendInvitation("teacher@test.edu", "https://app/invite?token=abc");

        verify(templateEngine).process(eq("email/invitation"), any(IContext.class));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendsCoverAssignedThroughItsOwnTemplate() {
        service.sendCoverAssigned("teacher@test.edu", "Maths", "8A", "2026-09-07");

        verify(templateEngine).process(eq("email/cover-assigned"), any(IContext.class));
    }

    @Test
    void sendsDelegationDecisionThroughItsOwnTemplate() {
        service.sendDelegationDecision("teacher@test.edu", "SWAP", "APPROVED", null);

        verify(templateEngine).process(eq("email/delegation-decision"), any(IContext.class));
    }

    @Test
    void sendsTimetablePublishedThroughItsOwnTemplate() {
        service.sendTimetablePublished("teacher@test.edu", "Autumn", "Term 1");

        verify(templateEngine).process(eq("email/timetable-published"), any(IContext.class));
    }

    @Test
    void aFailedSendIsSwallowedSoTheCallerIsUnaffected() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> service.sendInvitation("teacher@test.edu", "https://app/invite"))
                .doesNotThrowAnyException();
    }

    @Test
    void aTemplateFailureIsAlsoSwallowed() {
        when(templateEngine.process(anyString(), any(IContext.class)))
                .thenThrow(new IllegalStateException("bad template"));

        assertThatCode(() -> service.sendTimetablePublished("t@test.edu", "Autumn", "Term 1"))
                .doesNotThrowAnyException();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
