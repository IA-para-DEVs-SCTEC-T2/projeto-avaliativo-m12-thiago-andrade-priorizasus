package com.priorizasus.priorizasus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A 30-minute appointment window within clinic hours. Canonical term per CONTEXT.md.
 *
 * <p>Full implementation per Task-CM-01. A Slot has one Status at any time and is assigned to at
 * most one Patient. All Slots in Phase 1 are type BATCH.
 */
@Entity
@Table(name = "slots")
public class Slot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private LocalDate weekStart;

  @Column(nullable = false)
  private Instant slotDateTime;

  @Column(nullable = false)
  private int durationMinutes = 30;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private SlotType type = SlotType.BATCH;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SlotStatus status = SlotStatus.AVAILABLE;

  @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
  @jakarta.persistence.JoinColumn(name = "patient_id")
  private Patient patient;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  public Slot() {}

  @jakarta.persistence.PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  @jakarta.persistence.PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  // ── Getters / Setters ──

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public LocalDate getWeekStart() {
    return weekStart;
  }

  public void setWeekStart(LocalDate weekStart) {
    this.weekStart = weekStart;
  }

  public Instant getSlotDateTime() {
    return slotDateTime;
  }

  public void setSlotDateTime(Instant slotDateTime) {
    this.slotDateTime = slotDateTime;
  }

  public int getDurationMinutes() {
    return durationMinutes;
  }

  public void setDurationMinutes(int durationMinutes) {
    this.durationMinutes = durationMinutes;
  }

  public SlotStatus getStatus() {
    return status;
  }

  public void setStatus(SlotStatus status) {
    this.status = status;
  }

  public SlotType getType() {
    return type;
  }

  public void setType(SlotType type) {
    this.type = type;
  }

  public Patient getPatient() {
    return patient;
  }

  public void setPatient(Patient patient) {
    this.patient = patient;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
