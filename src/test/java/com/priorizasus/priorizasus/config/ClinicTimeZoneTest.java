package com.priorizasus.priorizasus.config;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Validates {@link ClinicTimeZone} BRT conversion correctness.
 *
 * <p>Brazil (America/Sao_Paulo) does not observe DST since 2019, so BRT is always UTC-3.
 */
class ClinicTimeZoneTest {

  private final ClinicTimeZone ctz = new ClinicTimeZone();

  @Test
  @DisplayName("getZone returns America/Sao_Paulo")
  void zoneIsSaoPaulo() {
    assertEquals(ZoneId.of("America/Sao_Paulo"), ctz.getZone());
    assertEquals("America/Sao_Paulo", ClinicTimeZone.CLINIC_ZONE_ID);
  }

  @Test
  @DisplayName("today() returns a date in clinic timezone")
  void todayReturnsDate() {
    LocalDate today = ctz.today();
    assertNotNull(today);
    // Should be close to system date (allow ±1 day for timezone edge)
    LocalDate systemToday = LocalDate.now();
    long diff = Math.abs(today.toEpochDay() - systemToday.toEpochDay());
    assertTrue(diff <= 1, "today() should be within 1 day of system date");
  }

  @Nested
  @DisplayName("UTC ↔ BRT Conversion")
  class UtcBrtConversion {

    @Test
    @DisplayName("UTC noon → BRT 09:00 (UTC-3)")
    void utcNoonToBrt() {
      // May 27, 2026 12:00 UTC = May 27, 2026 09:00 BRT
      Instant utcNoon = Instant.parse("2026-05-27T12:00:00Z");
      LocalDateTime brt = ctz.toClinicLocalDateTime(utcNoon);

      assertEquals(2026, brt.getYear());
      assertEquals(Month.MAY, brt.getMonth());
      assertEquals(27, brt.getDayOfMonth());
      assertEquals(9, brt.getHour());
      assertEquals(0, brt.getMinute());
    }

    @Test
    @DisplayName("BRT 08:00 → UTC 11:00")
    void brtMorningToUtc() {
      LocalDate date = LocalDate.of(2026, 5, 27);
      Instant utc = ctz.toUtc(date, 8, 0);

      // 08:00 BRT = 11:00 UTC
      ZonedDateTime utcZdt = utc.atZone(ZoneId.of("UTC"));
      assertEquals(11, utcZdt.getHour());
      assertEquals(0, utcZdt.getMinute());
      assertEquals(27, utcZdt.getDayOfMonth());
    }

    @Test
    @DisplayName("BRT LocalDateTime → UTC Instant round-trip")
    void brtToUtcRoundTrip() {
      LocalDateTime brtTime = LocalDateTime.of(2026, 5, 27, 14, 30);
      Instant utc = ctz.toUtc(brtTime);
      LocalDateTime back = ctz.toClinicLocalDateTime(utc);

      assertEquals(brtTime, back);
    }

    @Test
    @DisplayName("formatForDisplay formats BRT correctly")
    void formatForDisplay() {
      // 2026-05-27T11:00:00Z = 2026-05-27T08:00:00 BRT
      Instant utc = Instant.parse("2026-05-27T11:00:00Z");
      String formatted = ctz.formatForDisplay(utc);

      assertEquals("27/05/2026 08:00", formatted);
    }

    @Test
    @DisplayName("formatForDisplay with null returns empty string")
    void formatForDisplayNull() {
      assertEquals("", ctz.formatForDisplay(null));
    }
  }
}
