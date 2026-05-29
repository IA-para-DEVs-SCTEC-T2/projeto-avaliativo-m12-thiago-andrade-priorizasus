package com.priorizasus.priorizasus.dto;

import java.time.LocalDate;

/** DTO for Patient registration and display. Maps to Patient entity per Task-PM-05. */
public record PatientDTO(
    Long id,
    String name,
    String cpf,
    LocalDate birthDate,
    String phone,
    String email,
    String address,
    String status,
    LocalDate registrationDate,
    LocalDate lastConsultationDate,
    LocalDate targetDate) {}
