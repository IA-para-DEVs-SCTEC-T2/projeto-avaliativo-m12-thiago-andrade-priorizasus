package com.priorizasus.priorizasus.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppointmentTest {

  @Test
  @DisplayName("default constructor and defaults")
  void defaultConstructor() {
    Appointment a = new Appointment();
    assertNotNull(a);
    assertEquals(AppointmentStatus.CONFIRMED, a.getStatus());
  }

  @Test
  @DisplayName("getters and setters work")
  void settersAndGetters() {
    Appointment a = new Appointment();
    a.setId(5L);
    a.setWeekStart(LocalDate.of(2026, 6, 1));
    a.setStatus(AppointmentStatus.COMPLETED);

    Patient p = new Patient();
    p.setId(1L);
    a.setPatient(p);

    Slot s = new Slot();
    s.setId(10L);
    a.setSlot(s);

    assertEquals(5L, a.getId());
    assertEquals(LocalDate.of(2026, 6, 1), a.getWeekStart());
    assertEquals(AppointmentStatus.COMPLETED, a.getStatus());
    assertEquals(p, a.getPatient());
    assertEquals(s, a.getSlot());
  }
}
