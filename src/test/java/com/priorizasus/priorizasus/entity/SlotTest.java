package com.priorizasus.priorizasus.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlotTest {

  @Test
  @DisplayName("default constructor sets defaults")
  void defaultConstructor() {
    Slot s = new Slot();

    assertEquals(SlotStatus.AVAILABLE, s.getStatus());
    assertEquals(SlotType.BATCH, s.getType());
    assertEquals(30, s.getDurationMinutes());
  }

  @Test
  @DisplayName("getters and setters work")
  void settersAndGetters() {
    Slot s = new Slot();
    s.setId(1L);
    s.setWeekStart(LocalDate.of(2026, 6, 1));
    Instant dt = Instant.parse("2026-06-01T08:00:00Z");
    s.setSlotDateTime(dt);
    s.setStatus(SlotStatus.RESERVED);

    Patient p = new Patient();
    p.setId(5L);
    s.setPatient(p);

    assertEquals(1L, s.getId());
    assertEquals(LocalDate.of(2026, 6, 1), s.getWeekStart());
    assertEquals(dt, s.getSlotDateTime());
    assertEquals(SlotStatus.RESERVED, s.getStatus());
    assertEquals(p, s.getPatient());
  }

  @Test
  @DisplayName("patient can be set and cleared")
  void patientSetter() {
    Slot s = new Slot();
    assertNull(s.getPatient());

    Patient p = new Patient();
    p.setId(1L);
    s.setPatient(p);
    assertEquals(p, s.getPatient());

    s.setPatient(null);
    assertNull(s.getPatient());
  }
}
