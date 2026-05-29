package com.priorizasus.priorizasus.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BookingTokenTest {

  @Test
  @DisplayName("constructor sets token, patient, weekStart, expiresAt")
  void constructorSetsTokenAndExpiry() {
    Patient p = new Patient();
    p.setId(1L);
    LocalDate weekStart = LocalDate.now().plusDays(7);
    BookingToken t = new BookingToken(p, weekStart);
    assertNotNull(t.getToken());
    assertFalse(t.isUsed());
    assertNotNull(t.getExpiresAt());
    assertEquals(p, t.getPatient());
    assertEquals(weekStart, t.getWeekStart());
  }

  @Test
  @DisplayName("isExpired respects expiresAt")
  void isExpiredRespectsExpiresAt() {
    BookingToken t = new BookingToken();
    Instant now = Instant.now();
    t.setExpiresAt(now.minus(1, ChronoUnit.HOURS));
    assertTrue(t.isExpired());
    t.setExpiresAt(now.plus(1, ChronoUnit.HOURS));
    assertFalse(t.isExpired());
  }

  @Test
  @DisplayName("getters and setters work")
  void settersAndGetters() {
    BookingToken t = new BookingToken();
    t.setId(42L);
    t.setToken("tok-xyz");
    t.setUsed(true);
    Patient p = new Patient();
    p.setId(99L);
    t.setPatient(p);
    t.setWeekStart(LocalDate.of(2026, 6, 1));

    assertEquals(42L, t.getId());
    assertEquals("tok-xyz", t.getToken());
    assertTrue(t.isUsed());
    assertEquals(p, t.getPatient());
    assertEquals(LocalDate.of(2026, 6, 1), t.getWeekStart());
  }
}
