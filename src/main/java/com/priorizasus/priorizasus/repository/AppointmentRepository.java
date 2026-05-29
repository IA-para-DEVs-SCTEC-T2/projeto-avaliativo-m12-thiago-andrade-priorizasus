package com.priorizasus.priorizasus.repository;

import com.priorizasus.priorizasus.entity.Appointment;
import com.priorizasus.priorizasus.entity.AppointmentStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for {@link Appointment} entities. */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

  /** Finds all appointments for a given patient, ordered by slot date/time descending. */
  List<Appointment> findByPatientIdOrderByCreatedAtDesc(Long patientId);

  /** Finds all appointments for a specific week. */
  List<Appointment> findByWeekStart(LocalDate weekStart);

  /** Finds all appointments for a specific week with a given status. */
  List<Appointment> findByWeekStartAndStatus(LocalDate weekStart, AppointmentStatus status);

  /** Finds an appointment by its associated slot ID. */
  java.util.Optional<Appointment> findBySlotId(Long slotId);
}
