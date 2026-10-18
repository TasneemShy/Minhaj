package com.example.minhaj.service;

import com.example.minhaj.services.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @Test
    void testSendWelcomeEmail() {
        String testEmail = "test@example.com";
        String testName = "Tasneem";

        emailService.sendWelcomeEmail(testEmail, testName);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();

        assertEquals("test@example.com", sentMessage.getTo()[0]);
        assertEquals("Welcome to Minhaj Platform! 🚀", sentMessage.getSubject());
        assertTrue(sentMessage.getText().contains(testName));
    }
}