package com.priorizasus.priorizasus.dto;

import java.time.Instant;
import java.time.LocalDate;

/** DTO for Slot display in capacity queries. Maps to Slot entity per STACK.md conventions. */
public record SlotDTO(
    Long id,
    LocalDate weekStart,
    Instant slotDateTime,
    int durationMinutes,
    String type,
    String status,
    Long patientId,
    String patientName) {}
