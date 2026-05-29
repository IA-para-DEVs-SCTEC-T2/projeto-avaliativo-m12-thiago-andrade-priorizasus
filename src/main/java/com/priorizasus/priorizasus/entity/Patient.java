package com.priorizasus.priorizasus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A person registered in the clinic's catchment area.
 *
 * <p>Stub entity for Thymeleaf compilation. Full implementation per Task-PM-02. Canonical term:
 * Patient (not Client, User, Enrollee, Beneficiary, Applicant).
 */
@Entity
@Table(name = "patients")
public class Patient {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String cpf;

  @Column(nullable = false)
  private LocalDate birthDate;

  @Column private String phone;

  @Column private String email;

  @Column private String address;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PatientStatus status = PatientStatus.ACTIVE;

  @Column private LocalDate registrationDate;

  @Column private LocalDate lastConsultationDate;

  @Column private LocalDate targetDate;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @jakarta.persistence.Transient private List<Category> categories = new ArrayList<>();

  public Patient() {}

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
    this.registrationDate = LocalDate.now();
  }

  @PreUpdate
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCpf() {
    return cpf;
  }

  public void setCpf(String cpf) {
    this.cpf = cpf;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public void setBirthDate(LocalDate birthDate) {
    this.birthDate = birthDate;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public PatientStatus getStatus() {
    return status;
  }

  public void setStatus(PatientStatus status) {
    this.status = status;
  }

  public LocalDate getRegistrationDate() {
    return registrationDate;
  }

  public void setRegistrationDate(LocalDate registrationDate) {
    this.registrationDate = registrationDate;
  }

  public LocalDate getLastConsultationDate() {
    return lastConsultationDate;
  }

  public void setLastConsultationDate(LocalDate lastConsultationDate) {
    this.lastConsultationDate = lastConsultationDate;
  }

  public LocalDate getTargetDate() {
    return targetDate;
  }

  public void setTargetDate(LocalDate targetDate) {
    this.targetDate = targetDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public List<Category> getCategories() {
    return categories;
  }

  public void setCategories(List<Category> categories) {
    this.categories = categories;
  }
}
