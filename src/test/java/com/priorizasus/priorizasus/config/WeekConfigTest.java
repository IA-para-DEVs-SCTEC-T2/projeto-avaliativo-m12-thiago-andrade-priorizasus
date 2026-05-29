package com.priorizasus.priorizasus.config;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeekConfigTest {

  @Test
  @DisplayName("default working days are Monday-Friday")
  void defaultWorkingDays() {
    WeekConfig config = new WeekConfig();
    Set<DayOfWeek> workingDays = config.getWorkingDays();

    assertEquals(5, workingDays.size());
    assertTrue(workingDays.contains(DayOfWeek.MONDAY));
    assertTrue(workingDays.contains(DayOfWeek.FRIDAY));
    assertFalse(workingDays.contains(DayOfWeek.SUNDAY));
  }

  @Test
  @DisplayName("custom days string parses correctly")
  void customDays() {
    WeekConfig config = new WeekConfig();
    config.setDays("MONDAY,WEDNESDAY,FRIDAY");

    Set<DayOfWeek> workingDays = config.getWorkingDays();
    assertEquals(3, workingDays.size());
  }

  @Test
  @DisplayName("default open time is 08:00")
  void defaultOpenTime() {
    WeekConfig config = new WeekConfig();
    assertEquals(LocalTime.of(8, 0), config.getOpenTime());
  }

  @Test
  @DisplayName("default close time is 17:00")
  void defaultCloseTime() {
    WeekConfig config = new WeekConfig();
    assertEquals(LocalTime.of(17, 0), config.getCloseTime());
  }

  @Test
  @DisplayName("default slot duration is 30 minutes")
  void defaultSlotDuration() {
    WeekConfig config = new WeekConfig();
    assertEquals(30, config.getSlotDurationMinutes());
  }

  @Test
  @DisplayName("default morning only is true")
  void defaultMorningOnly() {
    WeekConfig config = new WeekConfig();
    assertTrue(config.isMorningOnly());
  }

  @Test
  @DisplayName("getters and setters work for all fields")
  void settersAndGetters() {
    WeekConfig config = new WeekConfig();
    config.setOpenTime("09:00");
    config.setCloseTime("18:00");
    config.setSlotDurationMinutes(60);
    config.setSlotsPerDay(6);
    config.setTotalWeeklySlots(30);
    config.setMorningOnly(false);

    assertEquals(LocalTime.of(9, 0), config.getOpenTime());
    assertEquals(LocalTime.of(18, 0), config.getCloseTime());
    assertEquals(60, config.getSlotDurationMinutes());
    assertEquals(6, config.getSlotsPerDay());
    assertEquals(30, config.getTotalWeeklySlots());
    assertFalse(config.isMorningOnly());
  }
}
