package com.priorizasus.priorizasus.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO for Appointment display in Patient and Staff dashboards. Maps to Appointment entity per
 * STACK.md conventions.
 */
public record AppointmentDTO(
    Long id,
    Long patientId,
    String patientName,
    Long slotId,
    Instant slotDateTime,
    String status,
    LocalDate weekStart,
    Instant confirmedAt,
    Instant completedAt,
    String notesFromConsultation) {}
