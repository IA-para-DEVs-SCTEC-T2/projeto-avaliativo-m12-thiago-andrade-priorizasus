package com.priorizasus.priorizasus.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.priorizasus.priorizasus.config.ClinicTimeZone;
import com.priorizasus.priorizasus.entity.BookingToken;
import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.service.AppointmentService;
import com.priorizasus.priorizasus.service.BookingService;
import com.priorizasus.priorizasus.service.CapacityService;
import com.priorizasus.priorizasus.service.PatientService;
import com.priorizasus.priorizasus.service.ScoringService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

  @Mock private PatientService patientService;
  @Mock private CapacityService capacityService;
  @Mock private AppointmentService appointmentService;
  @Mock private BookingService bookingService;
  @Mock private ScoringService scoringService;
  @Mock private ClinicTimeZone clinicTimeZone;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new BookingController(
                    patientService,
                    capacityService,
                    appointmentService,
                    bookingService,
                    clinicTimeZone,
                    scoringService))
            .build();
  }

  @Test
  @DisplayName("GET /booking/lookup shows CPF form")
  void lookupFormShowsPage() throws Exception {
    mockMvc
        .perform(get("/booking/lookup"))
        .andExpect(status().isOk())
        .andExpect(view().name("booking/lookup"));
  }

  @Test
  @DisplayName("POST /booking/lookup with valid CPF redirects to dashboard")
  void lookupCpfWithPatientRedirects() throws Exception {
    Patient p = new Patient();
    p.setId(1L);
    p.setName("Ana");
    when(patientService.findByCpf("12345678901")).thenReturn(Optional.of(p));
    when(bookingService.findActiveTokenForPatient(1L)).thenReturn(Optional.empty());

    mockMvc
        .perform(post("/booking/lookup").param("cpf", "123.456.789-01"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/booking/1"));
  }

  @Test
  @DisplayName("POST /booking/lookup with token redirects to slot selection")
  void lookupCpfWithTokenRedirects() throws Exception {
    Patient p = new Patient();
    p.setId(1L);
    BookingToken token = new BookingToken();
    token.setToken("abc123");
    when(patientService.findByCpf("12345678901")).thenReturn(Optional.of(p));
    when(bookingService.findActiveTokenForPatient(1L)).thenReturn(Optional.of(token));

    mockMvc
        .perform(post("/booking/lookup").param("cpf", "123.456.789-01"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/booking/select/abc123"));
  }

  @Test
  @DisplayName("POST /booking/lookup with invalid CPF shows error")
  void lookupCpfInvalidShowsError() throws Exception {
    mockMvc
        .perform(post("/booking/lookup").param("cpf", "123"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/booking/lookup"))
        .andExpect(flash().attributeExists("errorMessage"));
  }

  @Test
  @DisplayName("POST /booking/lookup with CPF not found shows error")
  void lookupCpfNotFoundShowsError() throws Exception {
    when(patientService.findByCpf("12345678901")).thenReturn(Optional.empty());

    mockMvc
        .perform(post("/booking/lookup").param("cpf", "123.456.789-01"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/booking/lookup"))
        .andExpect(flash().attributeExists("errorMessage"));
  }

  @Test
  @DisplayName("GET /booking/{patientId} shows dashboard")
  void patientDashboardShowsPage() throws Exception {
    Patient p = new Patient();
    p.setId(1L);
    p.setName("Ana");
    when(patientService.findById(1L)).thenReturn(Optional.of(p));
    when(bookingService.findActiveTokenForPatient(1L)).thenReturn(Optional.empty());
    when(scoringService.isPatientSelected(1L)).thenReturn(false);
    when(appointmentService.findByPatientId(1L)).thenReturn(List.of());
    when(clinicTimeZone.today()).thenReturn(LocalDate.of(2026, 5, 25));

    mockMvc
        .perform(get("/booking/1"))
        .andExpect(status().isOk())
        .andExpect(view().name("booking/dashboard"))
        .andExpect(model().attributeExists("patient"));
  }

  @Test
  @DisplayName("GET /booking/{patientId} shows error when not found")
  void patientDashboardShowsErrorWhenNotFound() throws Exception {
    when(patientService.findById(99L)).thenReturn(Optional.empty());

    mockMvc.perform(get("/booking/99")).andExpect(status().isOk()).andExpect(view().name("error"));
  }

  @Test
  @DisplayName("GET /booking/select/{token} shows slot selection")
  void selectSlotShowsPage() throws Exception {
    Patient p = new Patient();
    p.setId(1L);
    p.setName("Ana");
    when(bookingService.validateToken("abc123")).thenReturn(p);
    when(bookingService.getWeekStartForToken("abc123")).thenReturn(LocalDate.of(2026, 6, 1));
    when(capacityService.createWeeklySlots(any())).thenReturn(List.of());
    when(capacityService.getAvailableSlotsForWeek(any())).thenReturn(List.of());
    when(clinicTimeZone.today()).thenReturn(LocalDate.of(2026, 5, 28));

    mockMvc
        .perform(get("/booking/select/abc123"))
        .andExpect(status().isOk())
        .andExpect(view().name("booking/select-slot"))
        .andExpect(model().attributeExists("patient"))
        .andExpect(model().attributeExists("token"));
  }

  @Test
  @DisplayName("GET /booking/select/{token} shows error for invalid token")
  void selectSlotShowsErrorForInvalidToken() throws Exception {
    when(bookingService.validateToken("invalid"))
        .thenThrow(new IllegalStateException("Token expired"));

    mockMvc
        .perform(get("/booking/select/invalid"))
        .andExpect(status().isOk())
        .andExpect(view().name("error"))
        .andExpect(model().attributeExists("errorMessage"));
  }

  @Test
  @DisplayName("POST /booking/select/{token} confirms booking")
  void confirmBookingRedirects() throws Exception {
    mockMvc
        .perform(post("/booking/select/abc123").param("slotId", "5"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/booking/confirmation"))
        .andExpect(flash().attributeExists("successMessage"));
  }

  @Test
  @DisplayName("GET /booking/confirmation shows page")
  void confirmationShowsPage() throws Exception {
    mockMvc
        .perform(get("/booking/confirmation"))
        .andExpect(status().isOk())
        .andExpect(view().name("booking/confirmation"));
  }

  @Test
  @DisplayName("GET /booking/cancelled shows page")
  void cancelledShowsPage() throws Exception {
    mockMvc
        .perform(get("/booking/cancelled"))
        .andExpect(status().isOk())
        .andExpect(view().name("booking/cancelled"));
  }

  @Test
  @DisplayName("POST /booking/{patientId}/confirm confirms reserved slot")
  void confirmReservedSlotRedirects() throws Exception {
    mockMvc
        .perform(post("/booking/1/confirm").param("slotId", "5"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/booking/1"))
        .andExpect(flash().attributeExists("successMessage"));
  }

  @Test
  @DisplayName("POST /booking/{patientId}/direct creates direct booking")
  void directBookingRedirects() throws Exception {
    mockMvc
        .perform(post("/booking/1/direct").param("slotId", "5"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/booking/1"))
        .andExpect(flash().attributeExists("successMessage"));
  }

  @Test
  @DisplayName("POST /booking/{pid}/appointments/{aid}/cancel cancels")
  void cancelAppointmentRedirects() throws Exception {
    mockMvc
        .perform(post("/booking/1/appointments/10/cancel"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/booking/1"))
        .andExpect(flash().attributeExists("successMessage"));
  }
}
