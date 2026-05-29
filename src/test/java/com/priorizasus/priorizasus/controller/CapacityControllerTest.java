package com.priorizasus.priorizasus.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.priorizasus.priorizasus.config.ClinicTimeZone;
import com.priorizasus.priorizasus.service.CapacityService;
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
class CapacityControllerTest {

  @Mock private CapacityService capacityService;
  @Mock private ClinicTimeZone clinicTimeZone;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new CapacityController(capacityService, clinicTimeZone))
            .build();
  }

  @Test
  @DisplayName("GET /staff/capacity shows slot grid")
  void slotGridShowsPage() throws Exception {
    when(clinicTimeZone.today()).thenReturn(LocalDate.of(2026, 5, 25));
    when(capacityService.getSlotsForWeek(any())).thenReturn(List.of());

    mockMvc
        .perform(get("/staff/capacity"))
        .andExpect(status().isOk())
        .andExpect(view().name("staff/slot-grid"))
        .andExpect(model().attributeExists("slots"))
        .andExpect(model().attributeExists("weekStart"))
        .andExpect(model().attributeExists("dayHeaders"))
        .andExpect(model().attributeExists("timeHeaders"));
  }

  @Test
  @DisplayName("GET /staff/capacity?weekStartStr parses param")
  void slotGridWithWeekParam() throws Exception {
    when(capacityService.getSlotsForWeek(any())).thenReturn(List.of());

    mockMvc
        .perform(get("/staff/capacity").param("weekStartStr", "2026-06-01"))
        .andExpect(status().isOk())
        .andExpect(view().name("staff/slot-grid"));
  }
}
