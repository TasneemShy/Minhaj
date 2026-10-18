package com.example.minhaj.services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

// Emails are best-effort: they are sent off the request thread and a failed send
// is logged rather than propagated, so it never fails registration or password reset.
@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendWelcomeEmail(String toEmail, String studentName) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom("noreply@minhaj.com");
        message.setTo(toEmail);
        message.setSubject("Welcome to Minhaj Platform! 🚀");
        message.setText("Hello " + studentName + ",\n\n" +
                "We are thrilled to have you join our platform. We wish you an enjoyable learning journey!\n\n" +
                "The Minhaj Team.");
        send(message, "welcome");
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@minhaj.com");
        message.setTo(toEmail);
        message.setSubject("Password Reset Request - Minhaj Platform 🔒");

        String resetUrl = "http://localhost:8080/reset-password?token=" + token;

        message.setText("We received a request to reset your password.\n\n" +
                "Please use the following link to set a new password (valid for 15 minutes):\n" +
                resetUrl + "\n\n" +
                "If you did not make this request, please ignore this email.");

        send(message, "password reset");
    }

    private void send(SimpleMailMessage message, String kind) {
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.error("Failed to send {} email", kind, ex);
        }
    }
}
