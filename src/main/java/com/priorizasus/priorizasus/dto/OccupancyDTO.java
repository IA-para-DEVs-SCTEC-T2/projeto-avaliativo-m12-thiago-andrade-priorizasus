package com.priorizasus.priorizasus.dto;

import java.time.LocalDate;

/** DTO for occupancy metrics per Week. Used by OccupancyService per Task-SD-02. */
public record OccupancyDTO(
    LocalDate weekStart,
    int totalSlots,
    int bookedSlots,
    int cancelledSlots,
    int expiredSlots,
    double utilizationPercent,
    String alertLevel,
    java.util.Map<String, CategoryOccupancyDTO> byCategory) {}
