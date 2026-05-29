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
 * A confirmed medical visit. Canonical term per CONTEXT.md.
 *
 * <p>Stub entity for Thymeleaf compilation. Full implementation per Task-BK-01.
 */
@Entity
@Table(name = "appointments")
public class Appointment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
  @jakarta.persistence.JoinColumn(name = "patient_id", nullable = false)
  private Patient patient;

  @jakarta.persistence.OneToOne(fetch = jakarta.persistence.FetchType.LAZY)
  @jakarta.persistence.JoinColumn(name = "slot_id", nullable = false, unique = true)
  private Slot slot;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AppointmentStatus status = AppointmentStatus.CONFIRMED;

  @Column(nullable = false)
  private LocalDate weekStart;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  public Appointment() {}

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

  public Patient getPatient() {
    return patient;
  }

  public void setPatient(Patient patient) {
    this.patient = patient;
  }

  public Slot getSlot() {
    return slot;
  }

  public void setSlot(Slot slot) {
    this.slot = slot;
  }

  public AppointmentStatus getStatus() {
    return status;
  }

  public void setStatus(AppointmentStatus status) {
    this.status = status;
  }

  public LocalDate getWeekStart() {
    return weekStart;
  }

  public void setWeekStart(LocalDate weekStart) {
    this.weekStart = weekStart;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
