package com.priorizasus.priorizasus.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.priorizasus.priorizasus.config.ClinicTimeZone;
import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.service.AppointmentService;
import com.priorizasus.priorizasus.service.CapacityService;
import com.priorizasus.priorizasus.service.PatientService;
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
class PatientControllerTest {

  @Mock private PatientService patientService;
  @Mock private AppointmentService appointmentService;
  @Mock private CapacityService capacityService;
  @Mock private ClinicTimeZone clinicTimeZone;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new PatientController(
                    patientService, appointmentService, capacityService, clinicTimeZone))
            .build();
  }

  @Test
  @DisplayName("GET /patients lists patients")
  void listPatientsShowsPage() throws Exception {
    when(patientService.findAllActive()).thenReturn(List.of());
    when(clinicTimeZone.today()).thenReturn(LocalDate.of(2026, 5, 28));

    mockMvc
        .perform(get("/patients"))
        .andExpect(status().isOk())
        .andExpect(view().name("patients/list"))
        .andExpect(model().attributeExists("patients"));
  }

  @Test
  @DisplayName("GET /patients?search filters")
  void listPatientsWithSearch() throws Exception {
    Patient p = new Patient();
    p.setId(1L);
    p.setName("Maria");
    when(patientService.search("maria")).thenReturn(List.of(p));
    when(clinicTimeZone.today()).thenReturn(LocalDate.of(2026, 5, 28));

    mockMvc
        .perform(get("/patients").param("search", "maria"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("filterSearch", "maria"));
  }

  @Test
  @DisplayName("GET /patients/{id} shows detail")
  void patientDetailShowsPage() throws Exception {
    Patient p = new Patient();
    p.setId(1L);
    p.setName("Ana");
    when(patientService.findById(1L)).thenReturn(Optional.of(p));
    when(appointmentService.findByPatientId(1L)).thenReturn(List.of());
    when(clinicTimeZone.today()).thenReturn(LocalDate.of(2026, 5, 28));

    mockMvc
        .perform(get("/patients/1"))
        .andExpect(status().isOk())
        .andExpect(view().name("patients/detail"))
        .andExpect(model().attributeExists("patient"));
  }

  @Test
  @DisplayName("GET /patients/{id} shows error when not found")
  void patientDetailShowsErrorWhenNotFound() throws Exception {
    when(patientService.findById(99L)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/patients/99"))
        .andExpect(status().isOk())
        .andExpect(view().name("error"))
        .andExpect(model().attributeExists("errorMessage"));
  }

  @Test
  @DisplayName("GET /patients/new shows form")
  void newPatientFormShowsPage() throws Exception {
    when(clinicTimeZone.today()).thenReturn(LocalDate.of(2026, 5, 28));

    mockMvc
        .perform(get("/patients/new"))
        .andExpect(status().isOk())
        .andExpect(view().name("patients/register"))
        .andExpect(model().attributeExists("patient"));
  }

  @Test
  @DisplayName("POST /patients/register creates patient")
  void registerFromHomeRedirects() throws Exception {
    Patient saved = new Patient();
    saved.setId(1L);
    saved.setName("Novo");
    when(patientService.register(any())).thenReturn(saved);

    mockMvc
        .perform(post("/patients/register").param("name", "Novo").param("cpf", "12345678901"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"))
        .andExpect(flash().attributeExists("successMessage"));
  }

  @Test
  @DisplayName("POST /patients creates patient (staff)")
  void registerPatientRedirects() throws Exception {
    Patient saved = new Patient();
    saved.setId(1L);
    saved.setName("Novo");
    when(patientService.register(any())).thenReturn(saved);

    mockMvc
        .perform(post("/patients").param("name", "Novo").param("cpf", "12345678901"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/patients/1"))
        .andExpect(flash().attributeExists("successMessage"));
  }

  @Test
  @DisplayName("POST /patients handles error")
  void registerPatientHandlesError() throws Exception {
    when(patientService.register(any()))
        .thenThrow(new IllegalArgumentException("CPF already exists"));

    mockMvc
        .perform(post("/patients").param("name", "Novo").param("cpf", "12345678901"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/patients/new"))
        .andExpect(flash().attributeExists("errorMessage"));
  }

  @Test
  @DisplayName("GET /patients/{id}/edit shows edit form")
  void editPatientFormShowsPage() throws Exception {
    Patient p = new Patient();
    p.setId(1L);
    p.setName("Ana");
    when(patientService.findById(1L)).thenReturn(Optional.of(p));
    when(clinicTimeZone.today()).thenReturn(LocalDate.of(2026, 5, 28));

    mockMvc
        .perform(get("/patients/1/edit"))
        .andExpect(status().isOk())
        .andExpect(view().name("patients/edit"))
        .andExpect(model().attributeExists("patient"));
  }

  @Test
  @DisplayName("POST /patients/{id}/update updates patient")
  void updatePatientRedirects() throws Exception {
    Patient updated = new Patient();
    updated.setId(1L);
    updated.setName("Updated");
    when(patientService.update(any(Patient.class))).thenReturn(updated);

    mockMvc
        .perform(post("/patients/1/update").param("name", "Updated").param("cpf", "12345678901"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/patients/1"))
        .andExpect(flash().attributeExists("successMessage"));
  }

  @Test
  @DisplayName("POST /patients/{id}/delete soft-deletes")
  void deletePatientRedirects() throws Exception {
    mockMvc
        .perform(post("/patients/1/delete"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/patients"))
        .andExpect(flash().attributeExists("successMessage"));
  }

  @Test
  @DisplayName("GET /patients/{id}/categories/assign shows form")
  void assignCategoryFormShowsPage() throws Exception {
    Patient p = new Patient();
    p.setId(1L);
    p.setName("Ana");
    when(patientService.findById(1L)).thenReturn(Optional.of(p));

    mockMvc
        .perform(get("/patients/1/categories/assign"))
        .andExpect(status().isOk())
        .andExpect(view().name("patients/assign-category"))
        .andExpect(model().attributeExists("patient"));
  }

  @Test
  @DisplayName("POST /patients/{id}/categories assigns category")
  void assignCategoryRedirects() throws Exception {
    when(clinicTimeZone.today()).thenReturn(LocalDate.of(2026, 5, 28));

    mockMvc
        .perform(post("/patients/1/categories").param("categoryType", "PRENATAL"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/patients/1"))
        .andExpect(flash().attributeExists("successMessage"));
  }

  @Test
  @DisplayName("POST /patients/{id}/categories/{catId}/remove removes category")
  void removeCategoryRedirects() throws Exception {
    when(patientService.removeCategory(1L, 5L)).thenReturn(true);

    mockMvc
        .perform(post("/patients/1/categories/5/remove"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/patients/1"))
        .andExpect(flash().attributeExists("successMessage"));
  }

  @Test
  @DisplayName("POST /patients/{pid}/appointments/{aid}/complete completes")
  void completeAppointmentRedirects() throws Exception {
    mockMvc
        .perform(post("/patients/1/appointments/10/complete"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/patients/1"))
        .andExpect(flash().attributeExists("successMessage"));
  }

  @Test
  @DisplayName("POST /patients/{pid}/appointments/{aid}/cancel cancels")
  void cancelAppointmentRedirects() throws Exception {
    mockMvc
        .perform(post("/patients/1/appointments/10/cancel"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/patients/1"))
        .andExpect(flash().attributeExists("successMessage"));
  }
}
