package com.priorizasus.priorizasus.dto;

/**
 * DTO for Selection results visible to staff. Contains the full Ranking table: selected (rank 1–40)
 * and non-selected (rank 41+). Used by StaffController per Task-SA-05.
 */
public record SelectionResultDTO(
    String weekStart,
    int totalEligible,
    int totalSelected,
    String status,
    java.util.List<ScoredPatientDTO> selected,
    java.util.List<ScoredPatientDTO> waitlisted) {}
