package com.priorizasus.priorizasus.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Persistence-layer configuration.
 *
 * <p>Declares beans for UTC clock and clinic timezone. Per ADR-0003: all timestamps stored in UTC,
 * display in America/Sao_Paulo.
 */
@Configuration
public class PersistenceConfig {

  /**
   * Returns the clinic's timezone as a Spring-managed {@link ZoneId} bean. Used by {@link
   * ClinicTimeZone} and any component needing BRT conversions.
   */
  @Bean
  public ZoneId clinicZone() {
    return ZoneId.of(ClinicTimeZone.CLINIC_ZONE_ID);
  }

  /**
   * Returns a UTC {@link Clock} bean for deterministic timestamp generation. All {@code
   * Instant.now()} calls in entities use this clock for audit accuracy.
   */
  @Bean
  public Clock utcClock() {
    return Clock.systemUTC();
  }
}
