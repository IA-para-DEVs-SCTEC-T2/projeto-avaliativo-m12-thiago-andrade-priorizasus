package com.priorizasus.priorizasus.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.service.ScoringService;
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
class ScoringApiControllerTest {

  @Mock private ScoringService scoringService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new ScoringApiController(scoringService)).build();
  }

  @Test
  @DisplayName("GET /api/scoring/selection returns selection result")
  void getSelectionReturnsResult() throws Exception {
    when(scoringService.getCurrentSelection()).thenReturn(null);

    mockMvc.perform(get("/api/scoring/selection")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("POST /api/scoring/selection/run triggers selection")
  void runSelectionReturnsOk() throws Exception {
    Patient p = new Patient();
    p.setId(1L);
    p.setName("Test Patient");
    when(scoringService.executeWeeklySelection()).thenReturn(List.of(p));

    mockMvc
        .perform(post("/api/scoring/selection/run"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.selectedCount").value(1));
  }
}
