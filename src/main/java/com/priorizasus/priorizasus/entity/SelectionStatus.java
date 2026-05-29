package com.priorizasus.priorizasus.entity;

/**
 * Status of an individual Selection within a WeeklySelection.
 *
 * <ul>
 *   <li>{@code SELECTED}: Patient in top 40, Reservation created (Slot status RESERVED)
 *   <li>{@code BOOKED}: Patient confirmed Booking (Slot status BOOKED)
 *   <li>{@code RELEASED}: Staff override released this Selection (Slot returned to AVAILABLE)
 * </ul>
 */
public enum SelectionStatus {
  SELECTED,
  BOOKED,
  RELEASED
}
