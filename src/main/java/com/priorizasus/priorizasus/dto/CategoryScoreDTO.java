package com.priorizasus.priorizasus.dto;

/**
 * Per-Category score breakdown for a single Patient in the Weekly Selection. Used for audit
 * transparency per Task-SA-03.
 */
public record CategoryScoreDTO(String categoryType, int weight, int daysOverdue, int bonus) {}
