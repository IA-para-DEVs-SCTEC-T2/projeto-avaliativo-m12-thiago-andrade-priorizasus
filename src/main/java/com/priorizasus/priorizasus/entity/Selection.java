package com.priorizasus.priorizasus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A single assignment of one Patient to one BATCH Slot within a Weekly Selection. Canonical term
 * per CONTEXT.md.
 *
 * <p>Each Selection records the Patient's Score, rank, and the reserved Slot (if top 40). Patients
 * ranked 41+ have {@code slot = null} (waitlisted).
 */
@Entity
@Table(name = "selections")
public class Selection {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "weekly_selection_id", nullable = false)
  private WeeklySelection weeklySelection;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "patient_id", nullable = false)
  private Patient patient;

  @Column(nullable = false)
  private int score;

  @Column(nullable = false)
  private int rank;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "slot_id")
  private Slot slot;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private SelectionStatus status = SelectionStatus.SELECTED;

  /**
   * JSON-format per-Category weight breakdown for auditability. Example:
   * [{"type":"PRENATAL","weight":1000,"overdueDays":10,"bonus":100}]
   */
  @Column(length = 2000)
  private String weightBreakdown;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  public Selection() {}

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  // ── Getters / Setters ──

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public WeeklySelection getWeeklySelection() {
    return weeklySelection;
  }

  public void setWeeklySelection(WeeklySelection weeklySelection) {
    this.weeklySelection = weeklySelection;
  }

  public Patient getPatient() {
    return patient;
  }

  public void setPatient(Patient patient) {
    this.patient = patient;
  }

  public int getScore() {
    return score;
  }

  public void setScore(int score) {
    this.score = score;
  }

  public int getRank() {
    return rank;
  }

  public void setRank(int rank) {
    this.rank = rank;
  }

  public Slot getSlot() {
    return slot;
  }

  public void setSlot(Slot slot) {
    this.slot = slot;
  }

  public SelectionStatus getStatus() {
    return status;
  }

  public void setStatus(SelectionStatus status) {
    this.status = status;
  }

  public String getWeightBreakdown() {
    return weightBreakdown;
  }

  public void setWeightBreakdown(String weightBreakdown) {
    this.weightBreakdown = weightBreakdown;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
