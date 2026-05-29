package com.priorizasus.priorizasus.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.priorizasus.priorizasus.config.ClinicTimeZone;
import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.service.ScoringService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ScoringControllerTest {

  @Mock private ScoringService scoringService;
  @Mock private ClinicTimeZone clinicTimeZone;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ScoringController(scoringService, clinicTimeZone))
            .build();
  }

  @Test
  @DisplayName("GET /staff/weekly-selection shows selection")
  void weeklySelectionResultShowsPage() throws Exception {
    when(clinicTimeZone.today()).thenReturn(LocalDate.of(2026, 5, 25));
    when(scoringService.getCurrentSelection()).thenReturn(null);

    mockMvc
        .perform(get("/staff/weekly-selection"))
        .andExpect(status().isOk())
        .andExpect(view().name("staff/weekly-selection-result"))
        .andExpect(model().attributeExists("selected"))
        .andExpect(model().attributeExists("waitlistedPreview"));
  }

  @Test
  @DisplayName("POST /staff/weekly-selection/run triggers selection")
  void runWeeklySelectionRedirects() throws Exception {
    Patient p = new Patient();
    p.setId(1L);
    p.setName("Test");
    when(scoringService.executeWeeklySelection()).thenReturn(List.of(p));

    mockMvc
        .perform(post("/staff/weekly-selection/run"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/staff/weekly-selection"))
        .andExpect(flash().attributeExists("successMessage"));
  }

  @Test
  @DisplayName("POST /staff/weekly-selection/run handles error")
  void runWeeklySelectionHandlesError() throws Exception {
    when(scoringService.executeWeeklySelection())
        .thenThrow(new RuntimeException("Selection failed"));

    mockMvc
        .perform(post("/staff/weekly-selection/run"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/staff/weekly-selection"))
        .andExpect(flash().attributeExists("errorMessage"));
  }
}
