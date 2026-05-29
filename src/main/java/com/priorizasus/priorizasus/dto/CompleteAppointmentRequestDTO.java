package com.priorizasus.priorizasus.dto;

import java.time.Instant;

/** DTO for staff Mark Complete action per Task-SD-03. */
public record CompleteAppointmentRequestDTO(
    String statusAtArrival, Instant completedAt, String notesFromConsultation) {}
