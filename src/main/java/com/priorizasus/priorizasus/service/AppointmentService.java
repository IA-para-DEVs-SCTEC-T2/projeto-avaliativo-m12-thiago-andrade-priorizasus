package com.priorizasus.priorizasus.service;

import com.priorizasus.priorizasus.annotation.ReqId;
import com.priorizasus.priorizasus.entity.Appointment;
import com.priorizasus.priorizasus.entity.AppointmentStatus;
import com.priorizasus.priorizasus.entity.Slot;
import com.priorizasus.priorizasus.entity.SlotStatus;
import com.priorizasus.priorizasus.repository.AppointmentRepository;
import com.priorizasus.priorizasus.repository.SlotRepository;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for Appointment operations — retrieval, creation, cancellation, and completion. */
@Service
public class AppointmentService {

  private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

  private final AppointmentRepository appointmentRepository;
  private final SlotRepository slotRepository;

  public AppointmentService(
      AppointmentRepository appointmentRepository, SlotRepository slotRepository) {
    this.appointmentRepository = appointmentRepository;
    this.slotRepository = slotRepository;
  }

  @ReqId("BK-004")
  @Transactional(readOnly = true)
  public List<Appointment> findByPatientId(Long patientId) {
    log.debug("findByPatientId {}", patientId);
    return appointmentRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
  }

  @ReqId("SD-001")
  @Transactional(readOnly = true)
  public List<Appointment> findByWeekStartAndStatus(LocalDate weekStart, AppointmentStatus status) {
    log.debug("findByWeekStartAndStatus weekStart={} status={}", weekStart, status);
    return appointmentRepository.findByWeekStartAndStatus(weekStart, status);
  }

  @ReqId("BK-004")
  @Transactional
  public Appointment createAppointment(Long patientId, Long slotId) {
    log.debug("createAppointment patient {} slot {}", patientId, slotId);
    Slot slot =
        slotRepository
            .findById(slotId)
            .orElseThrow(() -> new IllegalStateException("Slot não encontrado."));

    Appointment appointment = new Appointment();
    appointment.setSlot(slot);
    appointment.setWeekStart(slot.getWeekStart());
    appointment.setStatus(AppointmentStatus.CONFIRMED);
    return appointmentRepository.save(appointment);
  }

  @ReqId("BK-005")
  @Transactional
  public void cancelAppointment(Long appointmentId) {
    log.debug("cancelAppointment {}", appointmentId);
    Appointment appointment =
        appointmentRepository
            .findById(appointmentId)
            .orElseThrow(() -> new IllegalStateException("Agendamento não encontrado."));
    if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
      throw new IllegalStateException("Somente agendamentos confirmados podem ser cancelados.");
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
  }

  @ReqId("SD-003")
  @Transactional
  public void completeAppointment(Long appointmentId, String statusAtArrival, String notes) {
    log.debug("completeAppointment {} status={} notes={}", appointmentId, statusAtArrival, notes);
    Appointment appointment =
        appointmentRepository
            .findById(appointmentId)
            .orElseThrow(() -> new IllegalStateException("Agendamento não encontrado."));
    if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
      throw new IllegalStateException("Somente agendamentos confirmados podem ser concluídos.");
    }
    appointment.setStatus(AppointmentStatus.COMPLETED);
    appointmentRepository.save(appointment);

    // Update patient's lastConsultationDate
    if (appointment.getPatient() != null) {
      appointment.getPatient().setLastConsultationDate(java.time.LocalDate.now());
    }

    if (appointment.getSlot() != null) {
      appointment.getSlot().setStatus(SlotStatus.BOOKED);
      slotRepository.save(appointment.getSlot());
    }
  }

  @ReqId("BK-005")
  @Transactional
  public void reassignAppointment(Long appointmentId, Long newSlotId) {
    log.debug("reassignAppointment appointment={} newSlot={}", appointmentId, newSlotId);
    Appointment appointment =
        appointmentRepository
            .findById(appointmentId)
            .orElseThrow(() -> new IllegalStateException("Agendamento não encontrado."));

    if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
      throw new IllegalStateException("Somente agendamentos confirmados podem ser reagendados.");
    }

    Slot currentSlot = appointment.getSlot();
    if (currentSlot == null) {
      throw new IllegalStateException("Agendamento sem horário vinculado.");
    }

    Slot newSlot =
        slotRepository
            .lockSlotForUpdate(newSlotId)
            .orElseThrow(() -> new IllegalStateException("Novo horário não encontrado."));

    if (newSlot.getStatus() != SlotStatus.AVAILABLE) {
      throw new IllegalStateException("O novo horário não está disponível.");
    }

    currentSlot.setStatus(SlotStatus.AVAILABLE);
    currentSlot.setPatient(null);
    slotRepository.save(currentSlot);

    newSlot.setStatus(SlotStatus.BOOKED);
    newSlot.setPatient(appointment.getPatient());
    slotRepository.save(newSlot);

    appointment.setSlot(newSlot);
    appointment.setWeekStart(newSlot.getWeekStart());
    appointment.setStatus(AppointmentStatus.CONFIRMED);
    appointmentRepository.save(appointment);
  }
}
