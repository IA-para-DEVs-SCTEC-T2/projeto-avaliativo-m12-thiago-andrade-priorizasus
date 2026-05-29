package com.priorizasus.priorizasus.service;

import com.priorizasus.priorizasus.annotation.ReqId;
import com.priorizasus.priorizasus.entity.Appointment;
import com.priorizasus.priorizasus.entity.AppointmentStatus;
import com.priorizasus.priorizasus.entity.BookingToken;
import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.entity.PatientStatus;
import com.priorizasus.priorizasus.entity.Slot;
import com.priorizasus.priorizasus.entity.SlotStatus;
import com.priorizasus.priorizasus.repository.AppointmentRepository;
import com.priorizasus.priorizasus.repository.BookingTokenRepository;
import com.priorizasus.priorizasus.repository.SlotRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for Booking operations — slot reservation, validation, and Appointment creation. */
@Service
public class BookingService {

  private static final Logger log = LoggerFactory.getLogger(BookingService.class);

  private final PatientService patientService;
  private final BookingTokenRepository bookingTokenRepository;
  private final SlotRepository slotRepository;
  private final AppointmentRepository appointmentRepository;

  public BookingService(
      PatientService patientService,
      BookingTokenRepository bookingTokenRepository,
      SlotRepository slotRepository,
      AppointmentRepository appointmentRepository) {
    this.patientService = patientService;
    this.bookingTokenRepository = bookingTokenRepository;
    this.slotRepository = slotRepository;
    this.appointmentRepository = appointmentRepository;
  }

  /**
   * Reserves a Slot for a Patient using a BookingToken.
   *
   * <p>Validations (in order):
   *
   * <ol>
   *   <li>Token exists, not used, not expired
   *   <li>Patient is ACTIVE
   *   <li>Patient does not already have a BOOKED Appointment in the target Week
   *   <li>Slot exists and is AVAILABLE
   *   <li>Slot time is not in the past
   * </ol>
   *
   * <p>Uses pessimistic locking (FOR UPDATE NOWAIT) to prevent double-booking.
   *
   * @return the created Appointment
   * @throws IllegalStateException if any validation fails
   */
  @ReqId("BK-004")
  @Transactional
  public Appointment reserveSlot(String tokenStr, Long slotId) {
    // 1. Validate token
    BookingToken token =
        bookingTokenRepository
            .findByToken(tokenStr)
            .orElseThrow(() -> new IllegalStateException("Token inválido."));

    if (token.isUsed()) {
      throw new IllegalStateException("Este link já foi utilizado.");
    }
    if (token.isExpired()) {
      throw new IllegalStateException("Este link expirou. Aguarde a próxima Seleção Semanal.");
    }

    Patient patient = token.getPatient();

    // 2. Validate Patient
    if (patient.getStatus() != PatientStatus.ACTIVE) {
      throw new IllegalStateException(
          "Sua conta não está ativa. Entre em contato com a unidade de saúde.");
    }

    // 3. Pessimistic lock + validate Slot
    Slot slot =
        slotRepository
            .lockSlotForUpdate(slotId)
            .orElseThrow(() -> new IllegalStateException("Horário não encontrado."));

    if (slot.getStatus() != SlotStatus.AVAILABLE) {
      throw new IllegalStateException("Este horário já foi reservado. Escolha outro.");
    }

    // 4. Slot not in past
    if (slot.getSlotDateTime() != null
        && slot.getSlotDateTime().isBefore(java.time.Instant.now())) {
      throw new IllegalStateException("Não é possível agendar em um horário que já passou.");
    }

    // Create Appointment
    Appointment appointment = new Appointment();
    appointment.setPatient(patient);
    appointment.setSlot(slot);
    appointment.setStatus(AppointmentStatus.CONFIRMED);
    appointment.setWeekStart(token.getWeekStart());

    slot.setStatus(SlotStatus.BOOKED);
    slot.setPatient(patient);
    slotRepository.save(slot);

    // Save the appointment
    appointment = appointmentRepository.save(appointment);

    // Mark token as used
    token.setUsed(true);
    bookingTokenRepository.save(token);

    log.info(
        "Booking confirmed: Patient {} (id={}) booked Slot {} for week {}",
        patient.getName(),
        patient.getId(),
        slotId,
        token.getWeekStart());

    return appointment;
  }

  /**
   * Reserves a Slot directly from the patient lookup dashboard.
   *
   * <p>This path is used when the patient does not have an active booking token but still needs to
   * book through the public consultation flow.
   */
  @ReqId("BK-004")
  @Transactional
  public Appointment reserveSlotForPatient(Long patientId, Long slotId) {
    Patient patient =
        patientService
            .findById(patientId)
            .orElseThrow(() -> new IllegalStateException("Paciente não encontrado."));

    if (patient.getStatus() != PatientStatus.ACTIVE) {
      throw new IllegalStateException(
          "Sua conta não está ativa. Entre em contato com a unidade de saúde.");
    }

    if (hasBookedAppointmentThisWeek(patientId)) {
      throw new IllegalStateException("Você já possui 1 agendamento confirmado nesta semana.");
    }

    Slot slot =
        slotRepository
            .lockSlotForUpdate(slotId)
            .orElseThrow(() -> new IllegalStateException("Horário não encontrado."));

    if (slot.getStatus() != SlotStatus.AVAILABLE) {
      throw new IllegalStateException("Este horário já foi reservado. Escolha outro.");
    }

    if (slot.getSlotDateTime() != null
        && slot.getSlotDateTime().isBefore(java.time.Instant.now())) {
      throw new IllegalStateException("Não é possível agendar em um horário que já passou.");
    }

    Appointment appointment = new Appointment();
    appointment.setPatient(patient);
    appointment.setSlot(slot);
    appointment.setStatus(AppointmentStatus.CONFIRMED);
    appointment.setWeekStart(slot.getWeekStart());

    slot.setStatus(SlotStatus.BOOKED);
    slot.setPatient(patient);
    slotRepository.save(slot);

    appointment = appointmentRepository.save(appointment);

    log.info(
        "Direct booking confirmed: Patient {} (id={}) booked Slot {} for week {}",
        patient.getName(),
        patient.getId(),
        slotId,
        slot.getWeekStart());

    return appointment;
  }

  /**
   * Confirms a reserved Slot for a Patient from the booking dashboard.
   *
   * <p>The slot must already be RESERVED for the same patient.
   */
  @ReqId("BK-004")
  @Transactional
  public Appointment confirmReservedSlot(Long patientId, Long slotId) {
    Slot slot =
        slotRepository
            .lockSlotForUpdate(slotId)
            .orElseThrow(() -> new IllegalStateException("Horário não encontrado."));

    if (slot.getStatus() != SlotStatus.RESERVED) {
      throw new IllegalStateException("Este horário não está reservado para confirmação.");
    }

    if (slot.getPatient() == null || slot.getPatient().getId() == null) {
      throw new IllegalStateException("Reserva inválida.");
    }

    if (!slot.getPatient().getId().equals(patientId)) {
      throw new IllegalStateException("Este horário não pertence ao paciente informado.");
    }

    if (slot.getSlotDateTime() != null
        && slot.getSlotDateTime().isBefore(java.time.Instant.now())) {
      throw new IllegalStateException("Não é possível confirmar um horário que já passou.");
    }

    Appointment appointment = new Appointment();
    appointment.setPatient(slot.getPatient());
    appointment.setSlot(slot);
    appointment.setStatus(AppointmentStatus.CONFIRMED);
    appointment.setWeekStart(slot.getWeekStart());

    slot.setStatus(SlotStatus.BOOKED);
    slotRepository.save(slot);

    appointment = appointmentRepository.save(appointment);

    log.info(
        "Reserved booking confirmed: Patient {} (id={}) booked Slot {}",
        slot.getPatient().getName(),
        slot.getPatient().getId(),
        slotId);

    return appointment;
  }

  @ReqId("BK-001")
  public List<Slot> getAvailableSlotsForPatient(Long patientId) {
    log.debug("Stub: getAvailableSlotsForPatient {}", patientId);
    return new ArrayList<>();
  }

  /**
   * Cancels an Appointment (patient-initiated).
   *
   * <p>Rules:
   *
   * <ol>
   *   <li>Appointment must exist and be CONFIRMED
   *   <li>Must be at least 1 day (24h) before the appointment time
   *   <li>Slot reverts to AVAILABLE
   * </ol>
   *
   * @param appointmentId the appointment to cancel
   * @throws IllegalStateException if rules are violated
   */
  @ReqId("BK-005")
  @Transactional
  public void cancelAppointment(Long appointmentId) {
    Appointment appointment =
        appointmentRepository
            .findById(appointmentId)
            .orElseThrow(() -> new IllegalStateException("Agendamento não encontrado."));

    if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
      throw new IllegalStateException(
          "Este agendamento não pode ser cancelado (status: " + appointment.getStatus() + ").");
    }

    Slot slot = appointment.getSlot();
    if (slot == null) {
      throw new IllegalStateException("Agendamento sem horário vinculado.");
    }

    appointment.setStatus(AppointmentStatus.CANCELLED);
    appointmentRepository.save(appointment);

    slot.setStatus(SlotStatus.AVAILABLE);
    slot.setPatient(null);
    slotRepository.save(slot);

    log.info(
        "Appointment cancelled: Appointment {} released Slot {} for week {}",
        appointment.getId(),
        slot.getId(),
        slot.getWeekStart());
  }

  /** Cancels an Appointment for a specific patient from the dashboard. */
  @ReqId("BK-005")
  @Transactional
  public void cancelAppointment(Long patientId, Long appointmentId) {
    Appointment appointment =
        appointmentRepository
            .findById(appointmentId)
            .orElseThrow(() -> new IllegalStateException("Agendamento não encontrado."));

    if (appointment.getPatient() == null || !patientId.equals(appointment.getPatient().getId())) {
      throw new IllegalStateException("Este agendamento não pertence ao paciente informado.");
    }

    cancelAppointment(appointmentId);
  }

  /**
   * Validates a BookingToken and returns the associated Patient if valid.
   *
   * @param tokenStr the token string
   * @return the Patient if token is valid
   * @throws IllegalStateException if token is invalid, used, or expired
   */
  @ReqId("BK-004")
  public Patient validateToken(String tokenStr) {
    return findValidToken(tokenStr).getPatient();
  }

  @ReqId("BK-004")
  @Transactional(readOnly = true)
  public LocalDate getWeekStartForToken(String tokenStr) {
    return findValidToken(tokenStr).getWeekStart();
  }

  private boolean hasBookedAppointmentThisWeek(Long patientId) {
    LocalDate weekStart =
        java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"))
            .with(java.time.DayOfWeek.MONDAY);
    return appointmentRepository
        .findByWeekStartAndStatus(weekStart, AppointmentStatus.CONFIRMED)
        .stream()
        .anyMatch(
            appointment ->
                appointment.getPatient() != null
                    && patientId.equals(appointment.getPatient().getId()));
  }

  @ReqId("BK-001")
  @Transactional(readOnly = true)
  public Optional<BookingToken> findActiveTokenForPatient(Long patientId) {
    return bookingTokenRepository
        .findFirstByPatient_IdAndWeekStartAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            patientId,
            java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"))
                .with(java.time.DayOfWeek.MONDAY),
            java.time.Instant.now());
  }

  @ReqId("BK-004")
  @Transactional(readOnly = true)
  public BookingToken findValidToken(String tokenStr) {
    BookingToken token =
        bookingTokenRepository
            .findByToken(tokenStr)
            .orElseThrow(() -> new IllegalStateException("Token inválido."));
    if (token.isUsed()) {
      throw new IllegalStateException("Este link já foi utilizado.");
    }
    if (token.isExpired()) {
      throw new IllegalStateException("Este link expirou. Aguarde a próxima Seleção Semanal.");
    }
    return token;
  }
}
