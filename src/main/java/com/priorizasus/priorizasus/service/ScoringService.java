package com.priorizasus.priorizasus.service;

import com.priorizasus.priorizasus.annotation.ReqId;
import com.priorizasus.priorizasus.entity.BookingToken;
import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.repository.BookingTokenRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weekly Selection — the core fairness algorithm.
 *
 * <p>Calculates a Score for every eligible Patient, ranks them, selects the top 40, generates
 * unique BookingTokens, and sends email notifications with booking links. Runs automatically Monday
 * 7 AM BRT (10h UTC), or on-demand via {@link #executeWeeklySelection()}.
 */
@Service
public class ScoringService {

  private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

  private final PatientService patientService;
  private final BookingTokenRepository bookingTokenRepository;
  private final Optional<EmailService> emailService;
  private final CapacityService capacityService;

  @Value("${app.base-url:http://localhost:8080}")
  private String baseUrl;

  private volatile SelectionResult lastSelection;

  public ScoringService(
      PatientService patientService,
      BookingTokenRepository bookingTokenRepository,
      Optional<EmailService> emailService,
      CapacityService capacityService) {
    this.patientService = patientService;
    this.bookingTokenRepository = bookingTokenRepository;
    this.emailService = emailService;
    this.capacityService = capacityService;
  }

  /** Scheduled execution: Monday 7 AM BRT = 10h UTC, per SA-004. */
  @Scheduled(cron = "0 0 7 * * MON", zone = "America/Sao_Paulo")
  @Transactional
  @ReqId("SA-004")
  public void scheduledWeeklySelection() {
    log.info("Scheduled Weekly Selection triggered at Monday 7 AM BRT");
    executeWeeklySelection();
  }

  /**
   * Executes the Weekly Selection atomically.
   *
   * <ol>
   *   <li>Fetch all eligible Patients (ACTIVE, with Category, no BOOKED in current Week, ≥7 days
   *       since last consultation)
   *   <li>Calculate Score per Patient
   *   <li>Rank by Score desc (tie-break: earliest targetDate, earliest id)
   *   <li>Select top 40
   *   <li>Generate BookingToken for each, send email
   * </ol>
   *
   * @return the list of selected Patients (max 40)
   */
  @ReqId("SA-004")
  @Transactional
  public List<Patient> executeWeeklySelection() {
    log.info("Starting Weekly Selection...");

    List<Patient> eligible = patientService.findAllActive();
    log.info("Total ACTIVE patients: {}", eligible.size());

    // Filter: must have at least one category and lastConsultationDate ≥ 7 days ago
    LocalDate today = LocalDate.now();
    List<Patient> filtered =
        eligible.stream()
            .filter(p -> p.getCategories() != null && !p.getCategories().isEmpty())
            .filter(
                p ->
                    p.getLastConsultationDate() == null
                        || ChronoUnit.DAYS.between(p.getLastConsultationDate(), today) >= 7)
            .toList();

    log.info("Eligible patients after 7-day filter: {}", filtered.size());

    // Calculate Score and rank
    List<RankedPatient> ranked = new ArrayList<>();
    for (Patient p : filtered) {
      int score = calculateScore(p, today);
      ranked.add(new RankedPatient(p, score));
    }

    ranked.sort(
        Comparator.comparingInt(RankedPatient::score)
            .reversed()
            .thenComparing(
                r ->
                    r.patient().getTargetDate() != null
                        ? r.patient().getTargetDate()
                        : LocalDate.MAX)
            .thenComparing(r -> r.patient().getId()));

    // Select top 40
    int limit = Math.min(40, ranked.size());
    List<Patient> selected = new ArrayList<>();
    List<Patient> waitlisted = new ArrayList<>();
    for (int i = 0; i < ranked.size(); i++) {
      if (i < limit) {
        selected.add(ranked.get(i).patient());
      } else {
        waitlisted.add(ranked.get(i).patient());
      }
    }

    log.info("Selected {} of {} eligible patients", selected.size(), ranked.size());

    // Target week: this Monday (or next if already past Friday)
    LocalDate targetWeek = today.with(java.time.DayOfWeek.MONDAY);

    // Create weekly slots for the week; booking is now patient-selected from AVAILABLE slots.
    List<com.priorizasus.priorizasus.entity.Slot> allSlots =
        capacityService.createWeeklySlots(targetWeek);
    log.info("Weekly slots ready: {} total slots available for patient choice", allSlots.size());

    // Generate tokens (email failure must not rollback selection)
    for (Patient p : selected) {
      try {
        BookingToken token = new BookingToken(p, targetWeek);
        bookingTokenRepository.save(token);
        String bookingUrl = baseUrl + "/booking/select/" + token.getToken();
        emailService.ifPresent(es -> es.sendBookingLink(p, token.getToken(), bookingUrl));
      } catch (Exception emailEx) {
        log.warn("Email failed for {}: {}", p.getId(), emailEx.getMessage());
      }
    }

    lastSelection = new SelectionResult(selected, waitlisted);

    log.info(
        "Weekly Selection complete. {} selected, {} waitlisted.",
        selected.size(),
        waitlisted.size());
    return selected;
  }

  @ReqId("SA-005")
  public SelectionResult getCurrentSelection() {
    return lastSelection;
  }

  @ReqId("SA-005")
  public boolean isPatientSelected(Long patientId) {
    if (patientId == null || lastSelection == null) {
      return false;
    }

    return lastSelection.selected().stream()
        .anyMatch(patient -> patient.getId() != null && patient.getId().equals(patientId));
  }

  /**
   * Calculates the Score for a Patient.
   *
   * <p>Formula: Score = sum(Category Weight) + sum(min(daysOverdue × 10, 500)) per Category.
   */
  private int calculateScore(Patient patient, LocalDate today) {
    int score = 0;
    if (patient.getCategories() == null) {
      return score;
    }
    for (var category : patient.getCategories()) {
      score += category.getWeight(today);
      if (patient.getTargetDate() != null) {
        long daysOverdue = Math.max(0, ChronoUnit.DAYS.between(patient.getTargetDate(), today));
        score += Math.min(daysOverdue * 10, 500);
      }
    }
    return score;
  }

  private record RankedPatient(Patient patient, int score) {}

  public record SelectionResult(List<Patient> selected, List<Patient> waitlisted) {
    public SelectionResult {
      selected = selected != null ? List.copyOf(selected) : List.of();
      waitlisted = waitlisted != null ? List.copyOf(waitlisted) : List.of();
    }
  }
}
