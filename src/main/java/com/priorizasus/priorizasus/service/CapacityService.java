package com.priorizasus.priorizasus.service;

import com.priorizasus.priorizasus.annotation.ReqId;
import com.priorizasus.priorizasus.entity.Slot;
import com.priorizasus.priorizasus.entity.SlotStatus;
import com.priorizasus.priorizasus.repository.SlotRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CapacityService {

  private static final Logger log = LoggerFactory.getLogger(CapacityService.class);

  private static final int SLOTS_PER_DAY = 8;
  private static final int START_HOUR = 8;
  private static final ZoneId CLINIC_ZONE = ZoneId.of("America/Sao_Paulo");

  private final SlotRepository slotRepository;

  public CapacityService(SlotRepository slotRepository) {
    this.slotRepository = slotRepository;
  }

  @ReqId("CM-005")
  @Transactional(readOnly = true)
  public List<Slot> getSlotsForWeek(LocalDate weekStart) {
    return slotRepository.findByWeekStart(weekStart);
  }

  @ReqId("CM-005")
  @Transactional(readOnly = true)
  public List<Slot> getAvailableSlots(Long patientId) {
    LocalDate weekStart = LocalDate.now(CLINIC_ZONE).with(java.time.DayOfWeek.MONDAY);
    return slotRepository.findByWeekStartAndStatus(weekStart, SlotStatus.AVAILABLE);
  }

  @ReqId("CM-005")
  @Transactional(readOnly = true)
  public List<Slot> getAvailableSlotsForWeek(LocalDate weekStart) {
    return slotRepository.findByWeekStartAndStatus(weekStart, SlotStatus.AVAILABLE);
  }

  /**
   * Creates 40 AVAILABLE Slots for the given Week (Monday–Friday, 08:00–11:30, 30 min each).
   * Idempotent: if slots already exist for this week, returns them without creating duplicates.
   */
  @ReqId("CM-006")
  @Transactional
  public List<Slot> createWeeklySlots(LocalDate weekStart) {
    List<Slot> existing = slotRepository.findByWeekStart(weekStart);
    if (!existing.isEmpty()) {
      log.info("{} slots already exist for week starting {}", existing.size(), weekStart);
      return existing;
    }

    List<Slot> slots = new ArrayList<>();
    for (int day = 0; day < 5; day++) {
      LocalDate date = weekStart.plusDays(day);
      for (int slotIdx = 0; slotIdx < SLOTS_PER_DAY; slotIdx++) {
        int hour = START_HOUR + slotIdx / 2;
        int minute = (slotIdx % 2) * 30;
        ZonedDateTime zdt = date.atTime(hour, minute).atZone(CLINIC_ZONE);
        Instant slotInstant = zdt.toInstant();

        Slot slot = new Slot();
        slot.setWeekStart(weekStart);
        slot.setSlotDateTime(slotInstant);
        slot.setDurationMinutes(30);
        slot.setStatus(SlotStatus.AVAILABLE);
        slots.add(slot);
      }
    }

    List<Slot> saved = slotRepository.saveAll(slots);
    log.info("Created {} slots for week starting {}", saved.size(), weekStart);
    return saved;
  }

  @ReqId("CM-005")
  @Transactional
  public void reserveSlotForPatient(Slot slot, com.priorizasus.priorizasus.entity.Patient patient) {
    slot.setStatus(SlotStatus.RESERVED);
    slot.setPatient(patient);
    slotRepository.save(slot);
  }

  /** Finds all slots reserved for a specific patient in the current or next week. */
  @Transactional(readOnly = true)
  public List<Slot> findReservedSlotsForPatient(Long patientId) {
    LocalDate weekStart = LocalDate.now(CLINIC_ZONE).with(java.time.DayOfWeek.MONDAY);
    // Check current week first, then next week
    List<Slot> slots = slotRepository.findByWeekStart(weekStart);
    List<Slot> reserved = new ArrayList<>();
    for (Slot s : slots) {
      if (s.getStatus() == SlotStatus.RESERVED
          && s.getPatient() != null
          && s.getPatient().getId().equals(patientId)) {
        reserved.add(s);
      }
    }
    if (reserved.isEmpty()) {
      // Also check next week
      LocalDate nextWeek = weekStart.plusWeeks(1);
      List<Slot> nextSlots = slotRepository.findByWeekStart(nextWeek);
      for (Slot s : nextSlots) {
        if (s.getStatus() == SlotStatus.RESERVED
            && s.getPatient() != null
            && s.getPatient().getId().equals(patientId)) {
          reserved.add(s);
        }
      }
    }
    return reserved;
  }
}
