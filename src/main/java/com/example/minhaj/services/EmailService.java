package com.example.minhaj.services;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendWelcomeEmail(String toEmail, String studentName) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom("noreply@minhaj.com");
        message.setTo(toEmail);
        message.setSubject("Welcome to Minhaj Platform! 🚀");
        message.setText("Hello " + studentName + ",\n\n" +
                "We are thrilled to have you join our platform. We wish you an enjoyable learning journey!\n\n" +
                "The Minhaj Team.");
        mailSender.send(message);
    }

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

        mailSender.send(message);
    }
}