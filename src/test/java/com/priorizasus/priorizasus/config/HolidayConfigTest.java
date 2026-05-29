package com.priorizasus.priorizasus.config;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HolidayConfigTest {

  @Test
  @DisplayName("default dates is empty")
  void defaultDatesEmpty() {
    HolidayConfig config = new HolidayConfig();
    assertTrue(config.getHolidayDates().isEmpty());
  }

  @Test
  @DisplayName("null dates returns empty set")
  void nullDatesReturnsEmpty() {
    HolidayConfig config = new HolidayConfig();
    config.setDates(null);

    assertTrue(config.getHolidayDates().isEmpty());
  }

  @Test
  @DisplayName("blank dates returns empty set")
  void blankDatesReturnsEmpty() {
    HolidayConfig config = new HolidayConfig();
    config.setDates("   ");

    assertTrue(config.getHolidayDates().isEmpty());
  }

  @Test
  @DisplayName("parses comma-separated dates")
  void parsesDates() {
    HolidayConfig config = new HolidayConfig();
    config.setDates("2026-06-15,2026-11-20");

    var holidays = config.getHolidayDates();
    assertEquals(2, holidays.size());
    assertTrue(holidays.contains(LocalDate.of(2026, 6, 15)));
    assertTrue(holidays.contains(LocalDate.of(2026, 11, 20)));
  }

  @Test
  @DisplayName("isHoliday returns true for holiday dates")
  void isHolidayDetects() {
    HolidayConfig config = new HolidayConfig();
    config.setDates("2026-12-25");

    assertTrue(config.isHoliday(LocalDate.of(2026, 12, 25)));
    assertFalse(config.isHoliday(LocalDate.of(2026, 12, 26)));
  }

  @Test
  @DisplayName("getDates returns the raw string")
  void getDatesReturnsRaw() {
    HolidayConfig config = new HolidayConfig();
    config.setDates("2026-01-01");

    assertEquals("2026-01-01", config.getDates());
  }
}
