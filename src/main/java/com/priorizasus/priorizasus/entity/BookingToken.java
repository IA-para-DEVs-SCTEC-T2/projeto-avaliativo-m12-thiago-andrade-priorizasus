package com.priorizasus.priorizasus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A unique token that allows a selected Patient to book a Slot for the target Week.
 *
 * <p>Generated during the Weekly Selection for each of the top 40 Patients. The token is embedded
 * in an email link. Once the Patient uses it to book a Slot, the token is marked as used. Tokens
 * expire after 48 hours.
 *
 * <p>Canonical term: BookingToken (not Link Token, Access Token, Booking Key).
 */
@Entity
@Table(name = "booking_tokens")
public class BookingToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, updatable = false)
  private String token;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "patient_id", nullable = false)
  private Patient patient;

  @Column(nullable = false)
  private LocalDate weekStart;

  @Column(nullable = false)
  private boolean used;

  @Column(nullable = false)
  private Instant expiresAt;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  public BookingToken() {}

  public BookingToken(Patient patient, LocalDate weekStart) {
    this.patient = patient;
    this.weekStart = weekStart;
    this.token = UUID.randomUUID().toString();
    this.used = false;
    this.expiresAt = Instant.now().plus(java.time.Duration.ofHours(48));
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
    if (token == null) {
      token = UUID.randomUUID().toString();
    }
    if (expiresAt == null) {
      expiresAt = now.plus(java.time.Duration.ofHours(48));
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  // ── Getters / Setters ──

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public Patient getPatient() {
    return patient;
  }

  public void setPatient(Patient patient) {
    this.patient = patient;
  }

  public LocalDate getWeekStart() {
    return weekStart;
  }

  public void setWeekStart(LocalDate weekStart) {
    this.weekStart = weekStart;
  }

  public boolean isUsed() {
    return used;
  }

  public void setUsed(boolean used) {
    this.used = used;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
