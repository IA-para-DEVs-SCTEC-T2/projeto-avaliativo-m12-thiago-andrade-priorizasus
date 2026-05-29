package com.priorizasus.priorizasus.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Records the result of a Weekly Selection execution. Canonical term per CONTEXT.md.
 *
 * <p>Created atomically each Monday 7 AM by {@code ScoringService.executeWeeklySelection()}.
 * Contains the list of individual {@link Selection} records — one per eligible Patient, ranked by
 * Score. Only one WeeklySelection exists per Week.
 */
@Entity
@Table(name = "weekly_selections")
public class WeeklySelection {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private LocalDate weekStart;

  @Column(nullable = false, updatable = false)
  private Instant executedAt;

  @Column(nullable = false)
  private int totalEligible;

  @Column(nullable = false)
  private int totalSelected;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private WeeklySelectionStatus status;

  @Column(length = 2000)
  private String errorMessage;

  @Column(nullable = false, length = 100)
  private String staffUser = "system";

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "weeklySelection", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Selection> selections = new ArrayList<>();

  public WeeklySelection() {}

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
    this.executedAt = Instant.now();
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

  public Instant getExecutedAt() {
    return executedAt;
  }

  public void setExecutedAt(Instant executedAt) {
    this.executedAt = executedAt;
  }

  public int getTotalEligible() {
    return totalEligible;
  }

  public void setTotalEligible(int totalEligible) {
    this.totalEligible = totalEligible;
  }

  public int getTotalSelected() {
    return totalSelected;
  }

  public void setTotalSelected(int totalSelected) {
    this.totalSelected = totalSelected;
  }

  public WeeklySelectionStatus getStatus() {
    return status;
  }

  public void setStatus(WeeklySelectionStatus status) {
    this.status = status;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getStaffUser() {
    return staffUser;
  }

  public void setStaffUser(String staffUser) {
    this.staffUser = staffUser;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<Selection> getSelections() {
    return selections;
  }

  public void setSelections(List<Selection> selections) {
    this.selections = selections;
  }
}
