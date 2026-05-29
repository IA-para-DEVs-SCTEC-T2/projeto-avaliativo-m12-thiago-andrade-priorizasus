package com.priorizasus.priorizasus.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.priorizasus.priorizasus.entity.*;
import com.priorizasus.priorizasus.repository.*;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

  @Mock private AppointmentRepository appointmentRepository;
  @Mock private SlotRepository slotRepository;

  private AppointmentService appointmentService;

  @BeforeEach
  void setUp() {
    appointmentService = new AppointmentService(appointmentRepository, slotRepository);
  }

  @Test
  void createAppointmentSavesConfirmed() {
    Slot slot = new Slot();
    slot.setId(5L);
    slot.setWeekStart(LocalDate.now());

    when(slotRepository.findById(5L)).thenReturn(java.util.Optional.of(slot));
    when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Appointment a = appointmentService.createAppointment(1L, 5L);
    assertNotNull(a);
    assertEquals(AppointmentStatus.CONFIRMED, a.getStatus());
  }

  @Test
  void cancelAppointmentReleasesSlot() {
    Slot slot = new Slot();
    slot.setId(2L);
    slot.setStatus(SlotStatus.BOOKED);

    Appointment appointment = new Appointment();
    appointment.setId(10L);
    appointment.setStatus(AppointmentStatus.CONFIRMED);
    appointment.setSlot(slot);

    when(appointmentRepository.findById(10L)).thenReturn(java.util.Optional.of(appointment));
    when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(slotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    appointmentService.cancelAppointment(10L);

    assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
    assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
  }

  @Test
  void completeAppointmentSetsCompletedAndUpdatesSlot() {
    Appointment appointment = new Appointment();
    appointment.setId(20L);
    appointment.setStatus(AppointmentStatus.CONFIRMED);
    Patient p = new Patient();
    p.setId(7L);
    appointment.setPatient(p);

    Slot slot = new Slot();
    slot.setId(3L);
    slot.setStatus(SlotStatus.BOOKED);
    appointment.setSlot(slot);

    when(appointmentRepository.findById(20L)).thenReturn(java.util.Optional.of(appointment));
    when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(slotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    appointmentService.completeAppointment(20L, "ok", "notes");

    assertEquals(AppointmentStatus.COMPLETED, appointment.getStatus());
    assertEquals(SlotStatus.BOOKED, slot.getStatus());
    assertNotNull(appointment.getPatient().getLastConsultationDate());
  }

  @Test
  void reassignAppointmentMovesToNewSlot() {
    Appointment appointment = new Appointment();
    appointment.setId(30L);
    appointment.setStatus(AppointmentStatus.CONFIRMED);
    Patient p = new Patient();
    p.setId(8L);
    appointment.setPatient(p);

    Slot oldSlot = new Slot();
    oldSlot.setId(4L);
    oldSlot.setStatus(SlotStatus.BOOKED);
    appointment.setSlot(oldSlot);

    Slot newSlot = new Slot();
    newSlot.setId(5L);
    newSlot.setStatus(SlotStatus.AVAILABLE);

    when(appointmentRepository.findById(30L)).thenReturn(java.util.Optional.of(appointment));
    when(slotRepository.lockSlotForUpdate(5L)).thenReturn(java.util.Optional.of(newSlot));
    when(slotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    appointmentService.reassignAppointment(30L, 5L);

    assertEquals(SlotStatus.AVAILABLE, oldSlot.getStatus());
    assertEquals(SlotStatus.BOOKED, newSlot.getStatus());
    assertEquals(newSlot, appointment.getSlot());
  }
}
