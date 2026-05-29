package com.priorizasus.priorizasus.entity;

/**
 * Categorizes every auditable action in the system.
 *
 * <p>Used by {@link AuditLog} to record immutable log entries for every system action. Per
 * ADR-0004, staff user is hardcoded as "system" in Phase 1.
 */
public enum AuditActionType {

  /** Full Weekly Selection executed (scoring-algorithm). */
  WEEKLY_SELECTION("Seleção Semanal"),

  /** Patient confirmed a Reservation → Appointment created (booking-system). */
  BOOKING("Agendamento"),

  /** Staff moved a Patient from one Slot to another (staff-dashboard). */
  REASSIGN("Reagendamento"),

  /** Staff cancelled a Reservation or Appointment, freeing the Slot (staff-dashboard). */
  RELEASE("Liberação"),

  /** Patient cancelled their own Appointment (booking-system). */
  CANCELLATION("Cancelamento"),

  /** Staff suspended a Patient (staff-dashboard). */
  SUSPENSION("Suspensão"),

  /** Staff marked an Appointment as COMPLETED after consultation (staff-dashboard). */
  COMPLETED("Concluído"),

  /** Generic Patient status change (ACTIVE ↔ SUSPENDED ↔ INACTIVE). */
  STATUS_CHANGE("Mudança de Status"),

  /** New Patient registered in the system (patient-master). */
  PATIENT_REGISTERED("Paciente Cadastrado");

  private final String displayName;

  AuditActionType(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
