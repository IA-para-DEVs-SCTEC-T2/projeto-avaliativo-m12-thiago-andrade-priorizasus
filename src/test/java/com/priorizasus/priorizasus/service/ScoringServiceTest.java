package com.priorizasus.priorizasus.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.priorizasus.priorizasus.entity.*;
import com.priorizasus.priorizasus.repository.BookingTokenRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

  @Mock private PatientService patientService;
  @Mock private BookingTokenRepository bookingTokenRepository;
  @Mock private EmailService emailService;
  @Mock private CapacityService capacityService;

  private ScoringService scoringService;

  @BeforeEach
  void setUp() {
    scoringService =
        new ScoringService(
            patientService, bookingTokenRepository, Optional.of(emailService), capacityService);
  }

  private Patient createPatient(Long id, String name, LocalDate targetDate, List<Category> cats) {
    Patient p = new Patient();
    p.setId(id);
    p.setName(name);
    p.setStatus(PatientStatus.ACTIVE);
    p.setCategories(cats);
    p.setTargetDate(targetDate);
    return p;
  }

  private Category createCategory(CategoryType type, LocalDate today, Integer gestationalWeeks) {
    Category c = new Category();
    c.setType(type);
    if (type == CategoryType.PRENATAL && gestationalWeeks != null) {
      c.setUltrasoundDate(today.minusDays(7));
      c.setGestationalWeeksAtUltrasound(gestationalWeeks);
    }
    return c;
  }

  @Nested
  @DisplayName("executeWeeklySelection")
  class ExecuteWeeklySelection {

    @Test
    @DisplayName("Selects top 40 patients by score with correct ranking")
    void selectsTop40ByScore() {
      LocalDate today = LocalDate.now();

      List<Patient> allPatients =
          java.util.stream.IntStream.rangeClosed(1, 50)
              .mapToObj(
                  i -> {
                    CategoryType type;
                    int gw = 0;
                    if (i <= 10) {
                      type = CategoryType.PRENATAL;
                      gw = 37;
                    } else if (i <= 30) {
                      type = CategoryType.CHILD;
                    } else if (i <= 40) {
                      type = CategoryType.PRENATAL;
                      gw = 31;
                    } else {
                      type = CategoryType.PRENATAL;
                      gw = 24;
                    }
                    Category cat = createCategory(type, today, gw);
                    return createPatient(
                        (long) i, "Patient " + i, today.minusDays(30 + i), List.of(cat));
                  })
              .toList();

      when(patientService.findAllActive()).thenReturn(allPatients);
      when(capacityService.createWeeklySlots(any(LocalDate.class))).thenReturn(List.of());

      List<Patient> selected = scoringService.executeWeeklySelection();

      assertEquals(40, selected.size());
      assertTrue(selected.get(0).getId() <= 10);
    }

    @Test
    @DisplayName("Filters out patients without categories")
    void filtersOutPatientsWithoutCategories() {
      Patient p = createPatient(1L, "No Cat", null, null);
      when(patientService.findAllActive()).thenReturn(List.of(p));
      when(capacityService.createWeeklySlots(any(LocalDate.class))).thenReturn(List.of());

      List<Patient> selected = scoringService.executeWeeklySelection();
      assertTrue(selected.isEmpty());
    }

    @Test
    @DisplayName("Filters out patients with recent consultation (< 7 days)")
    void filtersOutRecentConsultation() {
      LocalDate today = LocalDate.now();
      Patient p =
          createPatient(
              1L,
              "Recent",
              today.minusDays(3),
              List.of(createCategory(CategoryType.CHRONIC, today, null)));
      p.setLastConsultationDate(today.minusDays(3));
      when(patientService.findAllActive()).thenReturn(List.of(p));
      when(capacityService.createWeeklySlots(any(LocalDate.class))).thenReturn(List.of());

      List<Patient> selected = scoringService.executeWeeklySelection();
      assertTrue(selected.isEmpty());
    }

    @Test
    @DisplayName("Handles email failure gracefully without rollback")
    void handlesEmailFailureGracefully() {
      LocalDate today = LocalDate.now();
      Category cat = createCategory(CategoryType.CHRONIC, today, null);
      Patient p = createPatient(1L, "Email Fail", today.minusDays(30), List.of(cat));
      p.setLastConsultationDate(today.minusDays(30));

      when(patientService.findAllActive()).thenReturn(List.of(p));
      when(bookingTokenRepository.save(any(BookingToken.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      when(capacityService.createWeeklySlots(any(LocalDate.class))).thenReturn(List.of());
      doThrow(new RuntimeException("SMTP down"))
          .when(emailService)
          .sendBookingLink(any(), anyString(), anyString());

      List<Patient> selected = scoringService.executeWeeklySelection();

      assertEquals(1, selected.size());
      verify(bookingTokenRepository).save(any(BookingToken.class));
    }

    @Test
    @DisplayName("Returns empty list when no eligible patients")
    void returnsEmptyWhenNoEligiblePatients() {
      when(patientService.findAllActive()).thenReturn(List.of());
      List<Patient> selected = scoringService.executeWeeklySelection();
      assertTrue(selected.isEmpty());
    }

    @Test
    @DisplayName("Applies overdue cap per category (max 500)")
    void appliesOverdueCap() {
      LocalDate today = LocalDate.now();
      // Chronic patient very overdue -> 200 + capped 500 = 700
      Patient chronic =
          createPatient(
              1L,
              "Chronic",
              today.minusDays(200),
              List.of(createCategory(CategoryType.CHRONIC, today, null)));
      chronic.setLastConsultationDate(today.minusDays(200));
      // Prenatal patient with moderate weight 500 and no overdue
      Patient prenatal =
          createPatient(
              2L, "Prenatal", null, List.of(createCategory(CategoryType.PRENATAL, today, 30)));
      prenatal.setLastConsultationDate(today.minusDays(30));

      when(patientService.findAllActive()).thenReturn(List.of(chronic, prenatal));
      when(bookingTokenRepository.save(any(BookingToken.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      when(capacityService.createWeeklySlots(any(LocalDate.class))).thenReturn(List.of());

      List<Patient> selected = scoringService.executeWeeklySelection();
      assertEquals(2, selected.size());
      // chronic should rank higher (700) than prenatal (500)
      assertEquals(1L, selected.get(0).getId());
    }

    @Test
    @DisplayName("Tie-breaks by id when scores and targetDates equal")
    void tieBreaksById() {
      LocalDate today = LocalDate.now();
      Category cat = createCategory(CategoryType.CHRONIC, today, null);
      Patient a = createPatient(5L, "A", null, List.of(cat));
      Patient b = createPatient(6L, "B", null, List.of(cat));

      when(patientService.findAllActive()).thenReturn(List.of(b, a));
      when(bookingTokenRepository.save(any(BookingToken.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      when(capacityService.createWeeklySlots(any(LocalDate.class))).thenReturn(List.of());

      List<Patient> selected = scoringService.executeWeeklySelection();
      assertEquals(2, selected.size());
      // scores equal and targetDate null => tie-break by id ascending
      assertEquals(5L, selected.get(0).getId());
    }
  }

  @Nested
  @DisplayName("isPatientSelected")
  class IsPatientSelected {

    @Test
    @DisplayName("Returns true for selected patient")
    void selectedPatient() {
      LocalDate today = LocalDate.now();
      Category cat = createCategory(CategoryType.CHRONIC, today, null);
      Patient p = createPatient(1L, "Selected", today.minusDays(30), List.of(cat));
      p.setLastConsultationDate(today.minusDays(30));

      when(patientService.findAllActive()).thenReturn(List.of(p));
      when(bookingTokenRepository.save(any(BookingToken.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      when(capacityService.createWeeklySlots(any(LocalDate.class))).thenReturn(List.of());

      scoringService.executeWeeklySelection();

      assertTrue(scoringService.isPatientSelected(1L));
    }

    @Test
    @DisplayName("Returns false for null patientId")
    void nullPatientId() {
      assertFalse(scoringService.isPatientSelected(null));
    }

    @Test
    @DisplayName("Returns false when no selection has been run")
    void noSelectionYet() {
      assertFalse(scoringService.isPatientSelected(1L));
    }
  }
}
