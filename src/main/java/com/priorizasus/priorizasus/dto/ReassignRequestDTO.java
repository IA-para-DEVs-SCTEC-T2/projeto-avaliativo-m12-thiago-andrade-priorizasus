package com.priorizasus.priorizasus.dto;

/** DTO for staff Reassign action per Task-SD-01. */
public record ReassignRequestDTO(
    Long patientId, Long currentSlotId, Long newSlotId, String reason) {}
