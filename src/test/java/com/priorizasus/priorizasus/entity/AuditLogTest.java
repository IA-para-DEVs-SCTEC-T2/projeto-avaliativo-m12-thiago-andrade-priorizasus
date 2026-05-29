package com.priorizasus.priorizasus.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditLogTest {

  @Test
  @DisplayName("constructor sets action type, staff user, and details")
  void constructorSetsFields() {
    AuditLog log = new AuditLog(AuditActionType.BOOKING, "system", "Booking created");

    assertEquals(AuditActionType.BOOKING, log.getActionType());
    assertEquals("system", log.getStaffUser());
    assertEquals("Booking created", log.getDetails());
  }

  @Test
  @DisplayName("builder-style setters work")
  void builderSetters() {
    AuditLog log =
        new AuditLog(AuditActionType.RELEASE, "admin", "Released")
            .withPatientId(1L)
            .withSlotId(5L)
            .withAppointmentId(10L);

    assertEquals(AuditActionType.RELEASE, log.getActionType());
    assertEquals(1L, log.getPatientId());
    assertEquals(5L, log.getSlotId());
    assertEquals(10L, log.getAppointmentId());
  }

  @Test
  @DisplayName("defaults for optional fields are null")
  void defaultsAreNull() {
    AuditLog log = new AuditLog(AuditActionType.CANCELLATION, "system", "Cancelled");

    assertNull(log.getPatientId());
    assertNull(log.getSlotId());
    assertNull(log.getAppointmentId());
  }
}
