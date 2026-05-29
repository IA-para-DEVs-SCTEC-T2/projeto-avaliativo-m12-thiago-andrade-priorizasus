package com.priorizasus.priorizasus.dto;

/** DTO for per-Category occupancy breakdown. Used by OccupancyService per Task-SD-02. */
public record CategoryOccupancyDTO(
    String categoryType,
    int eligibleCount,
    int bookedCount,
    double coveragePercent,
    String alertLevel) {}
