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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A clinical grouping assigned to a Patient. Canonical term per CONTEXT.md.
 *
 * <p>Each Category has a type (PRENATAL, CHILD, CHRONIC) and contributes a weight to the Patient's
 * Score in the Weekly Selection. PRENATAL weight varies by gestational weeks; CHILD weight varies
 * by milestone; CHRONIC has a fixed weight.
 *
 * <p>A Patient may have multiple active Categories simultaneously (e.g., PRENATAL + CHRONIC). Each
 * Category has its own {@code targetDate} calculated per clinical rules (PM-003).
 */
@Entity
@Table(name = "patient_categories")
public class Category {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "patient_id", nullable = false)
  private Patient patient;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private CategoryType type;

  @Column(nullable = false)
  private boolean active = true;

  /** Clinical deadline for next consultation. Recalculated after each completed Appointment. */
  @Column private LocalDate targetDate;

  /** For PRENATAL: date of the most recent ultrasound. */
  @Column private LocalDate ultrasoundDate;

  /** For PRENATAL: gestational weeks at the time of the ultrasound. */
  @Column private Integer gestationalWeeksAtUltrasound;

  /** For CHILD: the last milestone completed (used to derive next milestone). */
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private ChildMilestone lastMilestoneCompleted;

  /** For CHRONIC: description of the condition (e.g., "Diabetes tipo 2"). */
  @Column(length = 500)
  private String conditionDescription;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  public Category() {}

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  /**
   * Returns the Category Weight for scoring, recalculated based on the given date.
   *
   * <p>Weights per CONTEXT.md:
   *
   * <ul>
   *   <li>PRENATAL 36+ weeks: 1000
   *   <li>PRENATAL 28–36 weeks: 500
   *   <li>PRENATAL &lt;28 weeks: 300
   *   <li>CHILD 0–30 days: 900
   *   <li>CHILD 1–12 months: 700
   *   <li>CHILD 1–3 years: 400
   *   <li>CHRONIC: 200
   * </ul>
   */
  public int getWeight(LocalDate today) {
    if (type == CategoryType.CHRONIC) {
      return 200;
    }
    if (type == CategoryType.PRENATAL
        && ultrasoundDate != null
        && gestationalWeeksAtUltrasound != null) {
      long daysSince = ChronoUnit.DAYS.between(ultrasoundDate, today);
      double currentWeeks = gestationalWeeksAtUltrasound + (daysSince / 7.0);
      if (currentWeeks >= 36) {
        return 1000;
      } else if (currentWeeks >= 28) {
        return 500;
      }
      return 300;
    }
    if (type == CategoryType.PRENATAL) {
      return 300; // default PRENATAL weight without ultrasound data
    }
    if (type == CategoryType.CHILD) {
      // Simplified: use a default CHILD weight (could be refined with milestones)
      return 700;
    }
    return 0;
  }

  // ── Getters / Setters ──

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public CategoryType getType() {
    return type;
  }

  public void setType(CategoryType type) {
    this.type = type;
  }

  public LocalDate getTargetDate() {
    return targetDate;
  }

  public void setTargetDate(LocalDate targetDate) {
    this.targetDate = targetDate;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public LocalDate getUltrasoundDate() {
    return ultrasoundDate;
  }

  public void setUltrasoundDate(LocalDate ultrasoundDate) {
    this.ultrasoundDate = ultrasoundDate;
  }

  public Integer getGestationalWeeksAtUltrasound() {
    return gestationalWeeksAtUltrasound;
  }

  public void setGestationalWeeksAtUltrasound(Integer gestationalWeeksAtUltrasound) {
    this.gestationalWeeksAtUltrasound = gestationalWeeksAtUltrasound;
  }

  public Patient getPatient() {
    return patient;
  }

  public void setPatient(Patient patient) {
    this.patient = patient;
  }

  public ChildMilestone getLastMilestoneCompleted() {
    return lastMilestoneCompleted;
  }

  public void setLastMilestoneCompleted(ChildMilestone lastMilestoneCompleted) {
    this.lastMilestoneCompleted = lastMilestoneCompleted;
  }

  public String getConditionDescription() {
    return conditionDescription;
  }

  public void setConditionDescription(String conditionDescription) {
    this.conditionDescription = conditionDescription;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
