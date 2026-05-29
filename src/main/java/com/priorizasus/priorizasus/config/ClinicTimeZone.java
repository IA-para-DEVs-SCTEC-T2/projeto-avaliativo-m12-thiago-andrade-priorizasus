package com.priorizasus.priorizasus.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Centralized timezone utility for the clinic's local timezone (America/Sao_Paulo).
 *
 * <p>Per ADR-0003: all timestamps are stored in UTC. Display and business-date calculations use the
 * clinic's local timezone. Duration calculations use {@link LocalDate} and {@link
 * java.time.temporal.ChronoUnit#DAYS}, never {@link java.time.Duration#between}.
 */
@Component
public class ClinicTimeZone {

  public static final String CLINIC_ZONE_ID = "America/Sao_Paulo";

  private static final ZoneId CLINIC_ZONE = ZoneId.of(CLINIC_ZONE_ID);
  private static final DateTimeFormatter BRT_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(CLINIC_ZONE);

  /** Returns the clinic's {@link ZoneId} (America/Sao_Paulo). */
  public ZoneId getZone() {
    return CLINIC_ZONE;
  }

  /** Returns today's date in the clinic timezone. */
  public LocalDate today() {
    return LocalDate.now(CLINIC_ZONE);
  }

  /**
   * Converts a UTC {@link Instant} to a display string in the clinic timezone.
   *
   * @param instant the UTC instant (nullable)
   * @return formatted string like "27/05/2026 08:00", or empty string if null
   */
  public String formatForDisplay(Instant instant) {
    if (instant == null) {
      return "";
    }
    return BRT_FORMATTER.format(instant);
  }

  /**
   * Converts a UTC {@link Instant} to a {@link LocalDateTime} in the clinic timezone.
   *
   * @param instant the UTC instant
   * @return LocalDateTime in clinic zone
   */
  public LocalDateTime toClinicLocalDateTime(Instant instant) {
    return LocalDateTime.ofInstant(instant, CLINIC_ZONE);
  }

  /**
   * Converts a clinic {@link LocalDateTime} (BRT) to a UTC {@link Instant}.
   *
   * @param localDateTime the clinic local date-time
   * @return UTC Instant
   */
  public Instant toUtc(LocalDateTime localDateTime) {
    return localDateTime.atZone(CLINIC_ZONE).toInstant();
  }

  /**
   * Converts a clinic date and time to a UTC {@link Instant}.
   *
   * @param date the clinic local date
   * @param hour hour (0-23) in clinic time
   * @param minute minute (0-59) in clinic time
   * @return UTC Instant
   */
  public Instant toUtc(LocalDate date, int hour, int minute) {
    return date.atTime(hour, minute).atZone(CLINIC_ZONE).toInstant();
  }
}
