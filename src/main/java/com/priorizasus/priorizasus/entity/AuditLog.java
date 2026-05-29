package com.priorizasus.priorizasus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Immutable audit trail entry for every system action.
 *
 * <p>Stored in UTC per ADR-0003. Queryable by action type, date range, and Patient. Phase 1
 * hardcodes {@code staffUser = "system"} per ADR-0004.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_type", nullable = false, length = 30)
  private AuditActionType actionType;

  @Column(name = "timestamp_utc", nullable = false, updatable = false)
  private Instant timestamp;

  @Column(name = "staff_user", nullable = false, length = 100)
  private String staffUser;

  @Column(name = "patient_id")
  private Long patientId;

  @Column(name = "slot_id")
  private Long slotId;

  @Column(name = "appointment_id")
  private Long appointmentId;

  @Column(name = "details", length = 2000)
  private String details;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AuditLog() {
    // JPA no-arg constructor
  }

  /**
   * Creates a new audit log entry with the given action type and details. Timestamp is auto-set via
   * {@link #onPersist()}.
   */
  public AuditLog(AuditActionType actionType, String staffUser, String details) {
    this.actionType = actionType;
    this.staffUser = staffUser;
    this.details = details;
  }

  @PrePersist
  void onPersist() {
    this.timestamp = Instant.now();
    this.createdAt = Instant.now();
  }

  // ── Getters ──

  public Long getId() {
    return id;
  }

  public AuditActionType getActionType() {
    return actionType;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public String getStaffUser() {
    return staffUser;
  }

  public Long getPatientId() {
    return patientId;
  }

  public Long getSlotId() {
    return slotId;
  }

  public Long getAppointmentId() {
    return appointmentId;
  }

  public String getDetails() {
    return details;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  // ── Builder-style setters for optional fields ──

  public AuditLog withPatientId(Long patientId) {
    this.patientId = patientId;
    return this;
  }

  public AuditLog withSlotId(Long slotId) {
    this.slotId = slotId;
    return this;
  }

  public AuditLog withAppointmentId(Long appointmentId) {
    this.appointmentId = appointmentId;
    return this;
  }
}
