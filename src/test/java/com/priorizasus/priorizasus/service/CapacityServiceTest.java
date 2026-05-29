package com.priorizasus.priorizasus.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.priorizasus.priorizasus.entity.Slot;
import com.priorizasus.priorizasus.entity.SlotStatus;
import com.priorizasus.priorizasus.repository.SlotRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CapacityServiceTest {

  @Mock private SlotRepository slotRepository;

  private CapacityService capacityService;

  @BeforeEach
  void setUp() {
    capacityService = new CapacityService(slotRepository);
  }

  @Test
  void createWeeklySlotsCreates40WhenNoneExist() {
    LocalDate weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
    when(slotRepository.findByWeekStart(weekStart)).thenReturn(new ArrayList<>());
    when(slotRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

    List<Slot> slots = capacityService.createWeeklySlots(weekStart);
    assertEquals(40, slots.size());
    assertTrue(slots.stream().allMatch(s -> s.getStatus() == SlotStatus.AVAILABLE));
  }

  @Test
  void createWeeklySlotsReturnsExistingIfPresent() {
    LocalDate weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
    List<Slot> existing = List.of(new Slot(), new Slot());
    when(slotRepository.findByWeekStart(weekStart)).thenReturn(existing);

    List<Slot> slots = capacityService.createWeeklySlots(weekStart);
    assertSame(existing, slots);
  }

  @Test
  void getSlotsForWeekDelegatesToRepository() {
    LocalDate weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
    Slot slot = new Slot();
    slot.setId(1L);
    when(slotRepository.findByWeekStart(weekStart)).thenReturn(List.of(slot));

    List<Slot> result = capacityService.getSlotsForWeek(weekStart);
    assertEquals(1, result.size());
  }

  @Test
  void getAvailableSlotsDelegatesToRepository() {
    LocalDate weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
    Slot slot = new Slot();
    slot.setId(1L);
    when(slotRepository.findByWeekStartAndStatus(weekStart, SlotStatus.AVAILABLE))
        .thenReturn(List.of(slot));

    List<Slot> result = capacityService.getAvailableSlots(1L);
    assertEquals(1, result.size());
  }

  @Test
  void getAvailableSlotsForWeekDelegatesToRepository() {
    LocalDate weekStart = LocalDate.now().plusWeeks(1).with(java.time.DayOfWeek.MONDAY);
    Slot slot = new Slot();
    slot.setId(2L);
    when(slotRepository.findByWeekStartAndStatus(weekStart, SlotStatus.AVAILABLE))
        .thenReturn(List.of(slot));

    List<Slot> result = capacityService.getAvailableSlotsForWeek(weekStart);
    assertEquals(1, result.size());
  }

  @Test
  void reserveSlotForPatientSetsReservedStatus() {
    Slot slot = new Slot();
    slot.setId(10L);
    slot.setStatus(SlotStatus.AVAILABLE);

    com.priorizasus.priorizasus.entity.Patient patient =
        new com.priorizasus.priorizasus.entity.Patient();
    patient.setId(5L);

    when(slotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    capacityService.reserveSlotForPatient(slot, patient);

    assertEquals(SlotStatus.RESERVED, slot.getStatus());
    assertEquals(patient, slot.getPatient());
    verify(slotRepository).save(slot);
  }

  @Test
  void findReservedSlotsForPatientReturnsMatchingSlots() {
    LocalDate weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY);

    com.priorizasus.priorizasus.entity.Patient patient =
        new com.priorizasus.priorizasus.entity.Patient();
    patient.setId(5L);

    Slot reserved = new Slot();
    reserved.setId(1L);
    reserved.setStatus(SlotStatus.RESERVED);
    reserved.setPatient(patient);

    Slot available = new Slot();
    available.setId(2L);
    available.setStatus(SlotStatus.AVAILABLE);

    when(slotRepository.findByWeekStart(weekStart)).thenReturn(List.of(reserved, available));

    List<Slot> result = capacityService.findReservedSlotsForPatient(5L);
    assertEquals(1, result.size());
    assertEquals(SlotStatus.RESERVED, result.get(0).getStatus());
  }
}
