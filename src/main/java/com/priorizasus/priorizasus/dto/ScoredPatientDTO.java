package com.priorizasus.priorizasus.dto;

import java.time.LocalDate;

/**
 * A scored Patient in the Weekly Selection Ranking. Immutable record used during scoring and
 * ranking per Task-SA-03.
 */
public record ScoredPatientDTO(
    Long patientId,
    String patientName,
    int score,
    int rank,
    Long slotId,
    LocalDate earliestTargetDate,
    String status,
    java.util.List<CategoryScoreDTO> categoryScores) {}
