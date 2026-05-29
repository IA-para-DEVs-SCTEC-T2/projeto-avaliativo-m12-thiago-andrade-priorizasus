package com.priorizasus.priorizasus.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StaffApiControllerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new StaffApiController()).build();
  }

  @Test
  @DisplayName("GET /api/staff/reports/export?type=occupancy returns CSV")
  void exportOccupancyReturnsCsv() throws Exception {
    mockMvc
        .perform(get("/api/staff/reports/export").param("type", "occupancy"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/csv; charset=UTF-8"));
  }
}
