package com.priorizasus.priorizasus.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeeklySelectionTest {

  @Test
  @DisplayName("default constructor works")
  void defaultConstructor() {
    WeeklySelection ws = new WeeklySelection();
    assertNotNull(ws);
  }

  @Test
  @DisplayName("getters and setters work")
  void settersAndGetters() {
    WeeklySelection ws = new WeeklySelection();
    ws.setId(1L);
    ws.setWeekStart(LocalDate.of(2026, 6, 1));
    ws.setTotalEligible(100);
    ws.setTotalSelected(40);
    ws.setStatus(WeeklySelectionStatus.COMPLETED);
    ws.setErrorMessage("Test error");
    Instant now = Instant.now();
    ws.setExecutedAt(now);

    assertEquals(1L, ws.getId());
    assertEquals(LocalDate.of(2026, 6, 1), ws.getWeekStart());
    assertEquals(100, ws.getTotalEligible());
    assertEquals(40, ws.getTotalSelected());
    assertEquals(WeeklySelectionStatus.COMPLETED, ws.getStatus());
    assertEquals("Test error", ws.getErrorMessage());
    assertEquals(now, ws.getExecutedAt());
  }

  @Test
  @DisplayName("selections list works")
  void selectionsList() {
    WeeklySelection ws = new WeeklySelection();
    Selection s = new Selection();
    s.setId(1L);
    ws.setSelections(List.of(s));

    assertEquals(1, ws.getSelections().size());
  }
}
