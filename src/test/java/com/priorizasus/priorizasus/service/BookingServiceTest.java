package com.priorizasus.priorizasus.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.priorizasus.priorizasus.entity.*;
import com.priorizasus.priorizasus.repository.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

  @Mock private PatientService patientService;
  @Mock private BookingTokenRepository bookingTokenRepository;
  @Mock private SlotRepository slotRepository;
  @Mock private AppointmentRepository appointmentRepository;

  private BookingService bookingService;

  @BeforeEach
  void setUp() {
    bookingService =
        new BookingService(
            patientService, bookingTokenRepository, slotRepository, appointmentRepository);
  }

  @Test
  void reserveSlotWithValidTokenCreatesAppointment() {
    Patient p = new Patient();
    p.setId(1L);
    p.setName("Test");
    p.setStatus(PatientStatus.ACTIVE);

    BookingToken token = new BookingToken(p, LocalDate.now());
    token.setUsed(false);
    token.setExpiresAt(Instant.now().plusSeconds(3600));

    Slot slot = new Slot();
    slot.setId(10L);
    slot.setStatus(SlotStatus.AVAILABLE);
    slot.setWeekStart(LocalDate.now());

    when(bookingTokenRepository.findByToken("t")).thenReturn(Optional.of(token));
    when(slotRepository.lockSlotForUpdate(10L)).thenReturn(Optional.of(slot));
    when(slotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(appointmentRepository.save(any()))
        .thenAnswer(
            inv -> {
              Appointment a = inv.getArgument(0);
              a.setId(99L);
              return a;
            });
    when(bookingTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Appointment result = bookingService.reserveSlot("t", 10L);

    assertNotNull(result);
    assertEquals(AppointmentStatus.CONFIRMED, result.getStatus());
    verify(bookingTokenRepository).save(any(BookingToken.class));
    assertTrue(token.isUsed());
  }

  @Test
  void reserveSlotWithExpiredTokenThrows() {
    Patient p = new Patient();
    p.setId(1L);
    p.setStatus(PatientStatus.ACTIVE);
    BookingToken token = new BookingToken(p, LocalDate.now());
    token.setExpiresAt(Instant.now().minusSeconds(10));

    when(bookingTokenRepository.findByToken("t")).thenReturn(Optional.of(token));

    assertThrows(IllegalStateException.class, () -> bookingService.reserveSlot("t", 1L));
  }

  @Test
  void reserveSlotForPatientDirectBooking() {
    Patient p = new Patient();
    p.setId(2L);
    p.setStatus(PatientStatus.ACTIVE);

    when(patientService.findById(2L)).thenReturn(Optional.of(p));

    Slot slot = new Slot();
    slot.setId(11L);
    slot.setStatus(SlotStatus.AVAILABLE);

    when(slotRepository.lockSlotForUpdate(11L)).thenReturn(Optional.of(slot));
    when(slotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Appointment ap = bookingService.reserveSlotForPatient(2L, 11L);
    assertNotNull(ap);
    assertEquals(AppointmentStatus.CONFIRMED, ap.getStatus());
    assertEquals(slot.getWeekStart(), ap.getWeekStart());
  }

  @Test
  void findValidTokenRejectsUsed() {
    Patient p = new Patient();
    p.setId(3L);
    BookingToken token = new BookingToken(p, LocalDate.now());
    token.setUsed(true);
    when(bookingTokenRepository.findByToken("x")).thenReturn(Optional.of(token));
    assertThrows(IllegalStateException.class, () -> bookingService.findValidToken("x"));
  }

  @Test
  void findActiveTokenForPatientReturnsOptional() {
    when(bookingTokenRepository
            .findFirstByPatient_IdAndWeekStartAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                anyLong(), any(LocalDate.class), any()))
        .thenReturn(Optional.empty());

    var res = bookingService.findActiveTokenForPatient(1L);
    assertTrue(res.isEmpty());
  }

  @Test
  void reserveSlotTokenNotFoundThrows() {
    when(bookingTokenRepository.findByToken("bad")).thenReturn(Optional.empty());
    assertThrows(IllegalStateException.class, () -> bookingService.reserveSlot("bad", 1L));
  }

  @Test
  void reserveSlotAlreadyUsedThrows() {
    Patient p = new Patient();
    p.setId(1L);
    BookingToken token = new BookingToken(p, LocalDate.now());
    token.setUsed(true);

    when(bookingTokenRepository.findByToken("t")).thenReturn(Optional.of(token));
    assertThrows(IllegalStateException.class, () -> bookingService.reserveSlot("t", 1L));
  }

  @Test
  void reserveSlotPatientNotActiveThrows() {
    Patient p = new Patient();
    p.setId(1L);
    p.setStatus(PatientStatus.INACTIVE);
    BookingToken token = new BookingToken(p, LocalDate.now());
    token.setExpiresAt(Instant.now().plusSeconds(3600));

    when(bookingTokenRepository.findByToken("t")).thenReturn(Optional.of(token));
    assertThrows(IllegalStateException.class, () -> bookingService.reserveSlot("t", 1L));
  }

  @Test
  void reserveSlotSlotNotFoundThrows() {
    Patient p = new Patient();
    p.setId(1L);
    p.setStatus(PatientStatus.ACTIVE);
    BookingToken token = new BookingToken(p, LocalDate.now());
    token.setExpiresAt(Instant.now().plusSeconds(3600));

    when(bookingTokenRepository.findByToken("t")).thenReturn(Optional.of(token));
    when(slotRepository.lockSlotForUpdate(99L)).thenReturn(Optional.empty());

    assertThrows(IllegalStateException.class, () -> bookingService.reserveSlot("t", 99L));
  }

  @Test
  void reserveSlotSlotNotAvailableThrows() {
    Patient p = new Patient();
    p.setId(1L);
    p.setStatus(PatientStatus.ACTIVE);
    BookingToken token = new BookingToken(p, LocalDate.now());
    token.setExpiresAt(Instant.now().plusSeconds(3600));

    Slot slot = new Slot();
    slot.setId(10L);
    slot.setStatus(SlotStatus.BOOKED);

    when(bookingTokenRepository.findByToken("t")).thenReturn(Optional.of(token));
    when(slotRepository.lockSlotForUpdate(10L)).thenReturn(Optional.of(slot));

    assertThrows(IllegalStateException.class, () -> bookingService.reserveSlot("t", 10L));
  }

  @Test
  void reserveSlotForPatientNotFoundThrows() {
    when(patientService.findById(99L)).thenReturn(Optional.empty());
    assertThrows(IllegalStateException.class, () -> bookingService.reserveSlotForPatient(99L, 1L));
  }

  @Test
  void reserveSlotForPatientNotActiveThrows() {
    Patient p = new Patient();
    p.setId(2L);
    p.setStatus(PatientStatus.INACTIVE);

    when(patientService.findById(2L)).thenReturn(Optional.of(p));
    assertThrows(IllegalStateException.class, () -> bookingService.reserveSlotForPatient(2L, 1L));
  }

  @Test
  void confirmReservedSlotSuccess() {
    Patient p = new Patient();
    p.setId(5L);
    p.setName("Confirm Patient");

    Slot slot = new Slot();
    slot.setId(20L);
    slot.setStatus(SlotStatus.RESERVED);
    slot.setPatient(p);
    slot.setWeekStart(LocalDate.now());
    slot.setSlotDateTime(Instant.now().plusSeconds(7200));

    when(slotRepository.lockSlotForUpdate(20L)).thenReturn(Optional.of(slot));
    when(slotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(appointmentRepository.save(any()))
        .thenAnswer(
            inv -> {
              Appointment a = inv.getArgument(0);
              a.setId(100L);
              return a;
            });

    Appointment result = bookingService.confirmReservedSlot(5L, 20L);

    assertNotNull(result);
    assertEquals(AppointmentStatus.CONFIRMED, result.getStatus());
    assertEquals(SlotStatus.BOOKED, slot.getStatus());
    verify(slotRepository).save(slot);
  }

  @Test
  void confirmReservedSlotWrongPatientThrows() {
    Patient p = new Patient();
    p.setId(5L);

    Slot slot = new Slot();
    slot.setId(20L);
    slot.setStatus(SlotStatus.RESERVED);
    slot.setPatient(p);

    when(slotRepository.lockSlotForUpdate(20L)).thenReturn(Optional.of(slot));

    assertThrows(IllegalStateException.class, () -> bookingService.confirmReservedSlot(99L, 20L));
  }

  @Test
  void confirmReservedSlotNotReservedThrows() {
    Slot slot = new Slot();
    slot.setId(20L);
    slot.setStatus(SlotStatus.AVAILABLE);

    when(slotRepository.lockSlotForUpdate(20L)).thenReturn(Optional.of(slot));

    assertThrows(IllegalStateException.class, () -> bookingService.confirmReservedSlot(1L, 20L));
  }

  @Test
  void cancelAppointmentSuccess() {
    Slot slot = new Slot();
    slot.setId(30L);
    slot.setStatus(SlotStatus.BOOKED);
    slot.setWeekStart(LocalDate.now());

    Appointment appointment = new Appointment();
    appointment.setId(200L);
    appointment.setStatus(AppointmentStatus.CONFIRMED);
    appointment.setSlot(slot);

    when(appointmentRepository.findById(200L)).thenReturn(Optional.of(appointment));
    when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(slotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    bookingService.cancelAppointment(200L);

    assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
    assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
    assertNull(slot.getPatient());
  }

  @Test
  void cancelAppointmentNotFoundThrows() {
    when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());
    assertThrows(IllegalStateException.class, () -> bookingService.cancelAppointment(999L));
  }

  @Test
  void cancelAppointmentAlreadyCancelledThrows() {
    Appointment appointment = new Appointment();
    appointment.setId(200L);
    appointment.setStatus(AppointmentStatus.CANCELLED);

    when(appointmentRepository.findById(200L)).thenReturn(Optional.of(appointment));

    assertThrows(IllegalStateException.class, () -> bookingService.cancelAppointment(200L));
  }

  @Test
  void cancelAppointmentWithPatientIdSuccess() {
    Patient p = new Patient();
    p.setId(7L);

    Slot slot = new Slot();
    slot.setId(31L);
    slot.setStatus(SlotStatus.BOOKED);
    slot.setWeekStart(LocalDate.now());

    Appointment appointment = new Appointment();
    appointment.setId(201L);
    appointment.setPatient(p);
    appointment.setStatus(AppointmentStatus.CONFIRMED);
    appointment.setSlot(slot);

    when(appointmentRepository.findById(201L)).thenReturn(Optional.of(appointment));
    when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(slotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    bookingService.cancelAppointment(7L, 201L);

    assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
  }

  @Test
  void cancelAppointmentWithPatientIdWrongPatientThrows() {
    Patient p = new Patient();
    p.setId(7L);

    Appointment appointment = new Appointment();
    appointment.setId(201L);
    appointment.setPatient(p);

    when(appointmentRepository.findById(201L)).thenReturn(Optional.of(appointment));

    assertThrows(IllegalStateException.class, () -> bookingService.cancelAppointment(99L, 201L));
  }

  @Test
  void validateTokenSuccess() {
    Patient p = new Patient();
    p.setId(1L);
    p.setStatus(PatientStatus.ACTIVE);
    BookingToken token = new BookingToken(p, LocalDate.now());
    token.setExpiresAt(Instant.now().plusSeconds(3600));

    when(bookingTokenRepository.findByToken("valid")).thenReturn(Optional.of(token));

    Patient result = bookingService.validateToken("valid");
    assertEquals(p, result);
  }

  @Test
  void getWeekStartForTokenSuccess() {
    Patient p = new Patient();
    p.setId(1L);
    p.setStatus(PatientStatus.ACTIVE);
    LocalDate ws = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
    BookingToken token = new BookingToken(p, ws);
    token.setExpiresAt(Instant.now().plusSeconds(3600));

    when(bookingTokenRepository.findByToken("valid")).thenReturn(Optional.of(token));

    LocalDate result = bookingService.getWeekStartForToken("valid");
    assertEquals(ws, result);
  }

  @Test
  void findValidTokenNotFoundThrows() {
    when(bookingTokenRepository.findByToken("missing")).thenReturn(Optional.empty());
    assertThrows(IllegalStateException.class, () -> bookingService.findValidToken("missing"));
  }

  @Test
  void findValidTokenExpiredThrows() {
    Patient p = new Patient();
    p.setId(1L);
    BookingToken token = new BookingToken(p, LocalDate.now());
    token.setExpiresAt(Instant.now().minusSeconds(3600));

    when(bookingTokenRepository.findByToken("exp")).thenReturn(Optional.of(token));
    assertThrows(IllegalStateException.class, () -> bookingService.findValidToken("exp"));
  }
}
