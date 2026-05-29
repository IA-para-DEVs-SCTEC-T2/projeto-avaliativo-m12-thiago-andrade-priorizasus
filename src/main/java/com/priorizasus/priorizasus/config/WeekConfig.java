package com.priorizasus.priorizasus.config;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Clinic operating schedule configuration per CM-002.
 *
 * <p>Binds to {@code clinic.schedule.*} properties in {@code application.properties}. Defines
 * working days, clinic hours, and slot distribution for capacity generation.
 */
@ConfigurationProperties(prefix = "clinic.schedule")
public class WeekConfig {

  /** Comma-separated working days (e.g., "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"). */
  private String days = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";

  /** Clinic opening time (HH:mm). */
  private String openTime = "08:00";

  /** Clinic closing time (HH:mm). */
  private String closeTime = "17:00";

  /** Duration of each Slot in minutes. */
  private int slotDurationMinutes = 30;

  /** Number of Slots per working day. */
  private int slotsPerDay = 8;

  /** Total weekly Slots. */
  private int totalWeeklySlots = 40;

  /** Whether only morning slots are created (Phase 1). */
  private boolean morningOnly = true;

  public Set<DayOfWeek> getWorkingDays() {
    return Stream.of(days.split(","))
        .map(String::trim)
        .map(String::toUpperCase)
        .map(DayOfWeek::valueOf)
        .collect(Collectors.toSet());
  }

  public LocalTime getOpenTime() {
    return LocalTime.parse(openTime);
  }

  public LocalTime getCloseTime() {
    return LocalTime.parse(closeTime);
  }

  public boolean isWorkingDay(LocalDate date) {
    return getWorkingDays().contains(date.getDayOfWeek());
  }

  // ── Getters / Setters ──

  public String getDays() {
    return days;
  }

  public void setDays(String days) {
    this.days = days;
  }

  public void setOpenTime(String openTime) {
    this.openTime = openTime;
  }

  public void setCloseTime(String closeTime) {
    this.closeTime = closeTime;
  }

  public int getSlotDurationMinutes() {
    return slotDurationMinutes;
  }

  public void setSlotDurationMinutes(int slotDurationMinutes) {
    this.slotDurationMinutes = slotDurationMinutes;
  }

  public int getSlotsPerDay() {
    return slotsPerDay;
  }

  public void setSlotsPerDay(int slotsPerDay) {
    this.slotsPerDay = slotsPerDay;
  }

  public int getTotalWeeklySlots() {
    return totalWeeklySlots;
  }

  public void setTotalWeeklySlots(int totalWeeklySlots) {
    this.totalWeeklySlots = totalWeeklySlots;
  }

  public boolean isMorningOnly() {
    return morningOnly;
  }

  public void setMorningOnly(boolean morningOnly) {
    this.morningOnly = morningOnly;
  }
}
