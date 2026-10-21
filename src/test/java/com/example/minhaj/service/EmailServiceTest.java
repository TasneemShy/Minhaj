package com.example.minhaj.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.minhaj.services.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, "https://app.example.com");
    }

    @Test
    void testSendWelcomeEmail() {
        String testEmail = "test@example.com";
        String testName = "Tasneem";

        emailService.sendWelcomeEmail(testEmail, testName, "verify-123");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();

        assertEquals("test@example.com", sentMessage.getTo()[0]);
        assertEquals("Welcome to Minhaj Platform! 🚀", sentMessage.getSubject());
        assertTrue(sentMessage.getText().contains(testName));
        assertTrue(sentMessage.getText().contains("https://app.example.com/verify-email?token=verify-123\n"),
                sentMessage.getText());
    }

    @Test
    void testSendVerificationEmailLinksToFrontendAndEncodesToken() {
        new EmailService(mailSender, "https://app.example.com/").sendVerificationEmail("test@example.com", "a+b/c=");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        assertEquals("test@example.com", messageCaptor.getValue().getTo()[0]);
        assertTrue(messageCaptor.getValue().getText().contains("https://app.example.com/verify-email?token=a%2Bb%2Fc%3D\n"),
                messageCaptor.getValue().getText());
    }

    @Test
    void testSendWelcomeEmailSwallowsAndLogsMailException() {
        MailSendException failure = new MailSendException("550 5.7.0 Too many emails per second");
        doThrow(failure).when(mailSender).send(any(SimpleMailMessage.class));

        Logger logger = (Logger) LoggerFactory.getLogger(EmailService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertDoesNotThrow(() -> emailService.sendWelcomeEmail("test@example.com", "Tasneem", "token"));
        } finally {
            logger.detachAppender(appender);
        }

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.ERROR, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("welcome"));
        assertEquals(failure.getMessage(), event.getThrowableProxy().getMessage());
    }

    @Test
    void testSendPasswordResetEmailLinksToFrontend() {
        emailService.sendPasswordResetEmail("test@example.com", "abc-_123");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertEquals("test@example.com", sentMessage.getTo()[0]);
        assertTrue(sentMessage.getText().contains("https://app.example.com/reset-password?token=abc-_123\n"),
                sentMessage.getText());
    }

    @Test
    void testSendPasswordResetEmailTrimsTrailingSlashAndEncodesToken() {
        new EmailService(mailSender, "https://app.example.com/").sendPasswordResetEmail("test@example.com", "a+b/c=");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        assertTrue(messageCaptor.getValue().getText().contains("https://app.example.com/reset-password?token=a%2Bb%2Fc%3D\n"),
                messageCaptor.getValue().getText());
    }

    @Test
    void testSendPasswordResetEmailSwallowsMailException() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> emailService.sendPasswordResetEmail("test@example.com", "token-123"));
    }
}
