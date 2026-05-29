package com.priorizasus.priorizasus.config;

import com.priorizasus.priorizasus.entity.Patient;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Shared model attributes exposed to all Thymeleaf templates via {@code @ControllerAdvice}.
 *
 * <p>Guardrail G15: {@code brtTime} and {@code today} must be available in every template so
 * timestamps are always displayed in BRT, never raw UTC. Per ADR-0003, all displayed times use the
 * clinic timezone ({@code America/Sao_Paulo}).
 *
 * <p>This is a top-level class (not an inner class of {@link WebConfig}) so Spring component
 * scanning reliably detects the {@code @ControllerAdvice} annotation.
 */
@ControllerAdvice
public class GlobalModelAttributes {

  private final ClinicTimeZone clinicTimeZone;

  public GlobalModelAttributes(ClinicTimeZone clinicTimeZone) {
    this.clinicTimeZone = clinicTimeZone;
  }

  /** Current time formatted for display in the clinic's timezone (America/Sao_Paulo). */
  @ModelAttribute("brtTime")
  public String brtTime() {
    return clinicTimeZone.formatForDisplay(Instant.now());
  }

  /** Today's date in the clinic's timezone. */
  @ModelAttribute("today")
  public LocalDate today() {
    return clinicTimeZone.today();
  }

  /** Exposes the ClinicTimeZone utility so templates can call formatForDisplay(). */
  @ModelAttribute("clinicTimeZone")
  public ClinicTimeZone clinicTimeZone() {
    return clinicTimeZone;
  }

  /** Empty Patient for registration form binding on the home page. */
  @ModelAttribute("patient")
  public Patient patient() {
    return new Patient();
  }
}
