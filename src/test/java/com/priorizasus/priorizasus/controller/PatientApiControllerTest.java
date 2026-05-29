package com.priorizasus.priorizasus.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.service.PatientService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PatientApiControllerTest {

  @Mock private PatientService patientService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new PatientApiController(patientService)).build();
  }

  @Test
  @DisplayName("GET /api/patients returns all active patients")
  void listPatientsReturnsAll() throws Exception {
    when(patientService.findAllActive()).thenReturn(List.of());

    mockMvc
        .perform(get("/api/patients"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"));
  }

  @Test
  @DisplayName("GET /api/patients?search=maria filters")
  void listPatientsWithSearch() throws Exception {
    Patient p = new Patient();
    p.setId(1L);
    p.setName("Maria");
    when(patientService.search("maria")).thenReturn(List.of(p));

    mockMvc
        .perform(get("/api/patients").param("search", "maria"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Maria"));
  }

  @Test
  @DisplayName("GET /api/patients?category=PRENATAL filters")
  void listPatientsWithCategory() throws Exception {
    when(patientService.findByCategory("PRENATAL")).thenReturn(List.of());

    mockMvc.perform(get("/api/patients").param("category", "PRENATAL")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET /api/patients/{id} returns patient")
  void getPatientReturnsOk() throws Exception {
    Patient p = new Patient();
    p.setId(1L);
    p.setName("Ana");
    when(patientService.findById(1L)).thenReturn(Optional.of(p));

    mockMvc
        .perform(get("/api/patients/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ana"));
  }

  @Test
  @DisplayName("GET /api/patients/{id} returns 404")
  void getPatientReturns404() throws Exception {
    when(patientService.findById(99L)).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/patients/99")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("POST /api/patients registers patient")
  void registerPatientReturns201() throws Exception {
    Patient saved = new Patient();
    saved.setId(1L);
    saved.setName("Novo");
    when(patientService.register(any())).thenReturn(saved);

    mockMvc
        .perform(
            post("/api/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo\",\"cpf\":\"12345678901\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1));
  }

  @Test
  @DisplayName("POST /api/patients returns 400 for invalid")
  void registerPatientReturns400() throws Exception {
    when(patientService.register(any()))
        .thenThrow(new IllegalArgumentException("CPF already exists"));

    mockMvc
        .perform(
            post("/api/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo\",\"cpf\":\"12345678901\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PUT /api/patients/{id} updates patient")
  void updatePatientReturnsOk() throws Exception {
    Patient updated = new Patient();
    updated.setId(1L);
    updated.setName("Updated");
    when(patientService.update(any(Patient.class))).thenReturn(updated);

    mockMvc
        .perform(
            put("/api/patients/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated"));
  }

  @Test
  @DisplayName("DELETE /api/patients/{id} deletes patient")
  void deletePatientReturnsNoContent() throws Exception {
    org.mockito.Mockito.doNothing().when(patientService).delete(1L);

    mockMvc.perform(delete("/api/patients/1")).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("DELETE /api/patients/{id} returns 404")
  void deletePatientReturns404() throws Exception {
    org.mockito.Mockito.doThrow(new jakarta.persistence.EntityNotFoundException("Not found"))
        .when(patientService)
        .delete(99L);

    mockMvc.perform(delete("/api/patients/99")).andExpect(status().isNotFound());
  }
}
