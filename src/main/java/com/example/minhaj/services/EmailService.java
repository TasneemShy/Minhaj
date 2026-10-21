package com.example.minhaj.services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

// Emails are best-effort: they are sent off the request thread and a failed send
// is logged rather than propagated, so it never fails registration or password reset.
@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String frontendUrl;

    // frontendUrl: base URL of the web app that serves the /reset-password and /verify-email pages (app.frontend-url).
    public EmailService(JavaMailSender mailSender, @Value("${app.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
    }

    // Sent on registration; carries the first email verification link.
    @Async
    public void sendWelcomeEmail(String toEmail, String studentName, String verificationToken) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom("noreply@minhaj.com");
        message.setTo(toEmail);
        message.setSubject("Welcome to Minhaj Platform! 🚀");
        message.setText("Hello " + studentName + ",\n\n" +
                "We are thrilled to have you join our platform. We wish you an enjoyable learning journey!\n\n" +
                "Please confirm your email address before enrolling in courses (the link is valid for 24 hours):\n" +
                verifyUrl(verificationToken) + "\n\n" +
                "The Minhaj Team.");
        send(message, "welcome");
    }

    @Async
    public void sendVerificationEmail(String toEmail, String verificationToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@minhaj.com");
        message.setTo(toEmail);
        message.setSubject("Confirm your email - Minhaj Platform ✉️");
        message.setText("Please use the following link to confirm your email address (valid for 24 hours):\n" +
                verifyUrl(verificationToken) + "\n\n" +
                "If you did not create a Minhaj account, please ignore this email.");
        send(message, "verification");
    }

    // The frontend page reads the token from the URL and POSTs it to /api/auth/verify-email.
    private String verifyUrl(String token) {
        return frontendUrl + "/verify-email?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@minhaj.com");
        message.setTo(toEmail);
        message.setSubject("Password Reset Request - Minhaj Platform 🔒");

        // The frontend page reads the token from the URL and POSTs it to /api/auth/reset-password.
        String resetUrl = frontendUrl + "/reset-password?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        message.setText("We received a request to reset your password.\n\n" +
                "Please use the following link to set a new password (valid for 15 minutes):\n" +
                resetUrl + "\n\n" +
                "If you did not make this request, please ignore this email.");

        send(message, "password reset");
    }

    // Sent when a lesson completion completes a course (see ProgressController.completeLesson).
    @Async
    public void sendCourseCompletedEmail(String toEmail, String studentName, Long courseId, String courseTitle) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@minhaj.com");
        message.setTo(toEmail);
        message.setSubject("Congratulations on completing " + courseTitle + "! 🎓");
        message.setText("Hello " + studentName + ",\n\n" +
                "You have completed every lesson of \"" + courseTitle + "\". Well done!\n\n" +
                "Your certificate of completion is ready:\n" +
                frontendUrl + "/courses/" + courseId + "/certificate\n\n" +
                "The Minhaj Team.");
        send(message, "course completion");
    }

    private void send(SimpleMailMessage message, String kind) {
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.error("Failed to send {} email", kind, ex);
        }
    }
}
