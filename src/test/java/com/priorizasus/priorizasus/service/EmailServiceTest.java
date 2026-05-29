package com.priorizasus.priorizasus.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.priorizasus.priorizasus.entity.Patient;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EmailServiceTest {

  private EmailService emailService;

  @BeforeEach
  void setUp() {
    // will set mailSender per-test
  }

  @Test
  void sendsEmailWhenSmtpConfigured() throws Exception {
    java.util.concurrent.atomic.AtomicBoolean sent =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    JavaMailSender mailSender =
        new org.springframework.mail.javamail.JavaMailSenderImpl() {
          @Override
          public void send(SimpleMailMessage simpleMessage) {
            sent.set(true);
          }

          @Override
          public void send(SimpleMailMessage... simpleMessages) {
            sent.set(true);
          }
        };

    emailService = new EmailService(mailSender);
    Field f = EmailService.class.getDeclaredField("mailUsername");
    f.setAccessible(true);
    f.set(emailService, "noreply@x.com");

    Patient p = new Patient();
    p.setName("Cli");
    p.setEmail("a@b.com");

    emailService.sendBookingLink(p, "token", "http://link");
    assertTrue(sent.get());
  }

  @Test
  void logsWhenNoSmtpConfigured() throws Exception {
    JavaMailSender mailSender = new org.springframework.mail.javamail.JavaMailSenderImpl();
    emailService = new EmailService(mailSender);
    Field f = EmailService.class.getDeclaredField("mailUsername");
    f.setAccessible(true);
    f.set(emailService, "");

    Patient p = new Patient();
    p.setName("Cli");
    p.setEmail("a@b.com");

    // Should not throw - mailSender is a noop implementation here
    emailService.sendBookingLink(p, "token", "http://link");
  }
}
