package com.priorizasus.priorizasus.service;

import com.priorizasus.priorizasus.annotation.ReqId;
import com.priorizasus.priorizasus.entity.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails — primarily booking-link emails to Patients selected in the Weekly
 * Selection.
 *
 * <p>Only activated when a {@link JavaMailSender} bean is available (SMTP configured). When SMTP
 * credentials are not configured, this bean is not created and {@code ScoringService} falls back to
 * console logging as a development fallback.
 */
@Service
@ConditionalOnBean(JavaMailSender.class)
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username:}")
  private String mailUsername;

  public EmailService(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  /**
   * Sends a booking-link email to the selected Patient.
   *
   * @param patient the selected Patient
   * @param token the unique booking token
   * @param bookingUrl the full URL the Patient should click
   */
  @ReqId("BK-004")
  public void sendBookingLink(Patient patient, String token, String bookingUrl) {
    String subject = "PRIORIZASUS — Seu horário de consulta está disponível";
    String body =
        "Olá, "
            + patient.getName()
            + "!\n\n"
            + "Você foi selecionado(a) para agendar sua consulta na Unidade de Saúde da Família.\n\n"
            + "Acesse o link abaixo para escolher o melhor horário para você:\n"
            + bookingUrl
            + "\n\n"
            + "Este link é pessoal e válido por 48 horas. "
            + "Os horários são preenchidos por ordem de acesso — não deixe para depois!\n\n"
            + "Atenciosamente,\n"
            + "Equipe PRIORIZASUS";

    if (mailUsername == null || mailUsername.isBlank()) {
      log.info(
          "SMTP not configured — logging email to console:\nTo: {}\nSubject: {}\nBody:\n{}",
          patient.getEmail(),
          subject,
          body);
      return;
    }

    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setTo(patient.getEmail());
      message.setSubject(subject);
      message.setText(body);
      mailSender.send(message);
      log.info("Booking-link email sent to {} at {}", patient.getName(), patient.getEmail());
    } catch (Exception e) {
      log.error("Failed to send email to {}: {}", patient.getEmail(), e.getMessage());
      log.info("Fallback — booking link for {}: {}", patient.getName(), bookingUrl);
    }
  }
}
