package com.priorizasus.priorizasus.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SelectionTest {

  @Test
  @DisplayName("default constructor and defaults")
  void defaultConstructor() {
    Selection s = new Selection();
    assertNotNull(s);
    assertEquals(SelectionStatus.SELECTED, s.getStatus());
  }

  @Test
  @DisplayName("getters and setters work")
  void settersAndGetters() {
    Selection s = new Selection();
    s.setId(7L);
    s.setScore(950);
    s.setRank(5);
    s.setStatus(SelectionStatus.BOOKED);
    s.setWeightBreakdown("[{\"type\":\"PRENATAL\",\"weight\":1000}]");

    assertEquals(7L, s.getId());
    assertEquals(950, s.getScore());
    assertEquals(5, s.getRank());
    assertEquals(SelectionStatus.BOOKED, s.getStatus());
  }

  @Test
  @DisplayName("patient and slot relationships")
  void patientAndSlot() {
    Patient p = new Patient();
    p.setId(1L);
    Slot slot = new Slot();
    slot.setId(10L);

    Selection s = new Selection();
    s.setPatient(p);
    s.setSlot(slot);

    assertEquals(p, s.getPatient());
    assertEquals(slot, s.getSlot());
  }

  @Test
  @DisplayName("weeklySelection relationship")
  void weeklySelectionRelation() {
    WeeklySelection ws = new WeeklySelection();
    ws.setId(100L);
    Selection s = new Selection();
    s.setWeeklySelection(ws);

    assertEquals(ws, s.getWeeklySelection());
  }
}
