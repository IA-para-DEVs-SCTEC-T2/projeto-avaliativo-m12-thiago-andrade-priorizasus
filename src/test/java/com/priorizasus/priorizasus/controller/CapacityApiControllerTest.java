package com.priorizasus.priorizasus.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.priorizasus.priorizasus.entity.Slot;
import com.priorizasus.priorizasus.entity.SlotStatus;
import com.priorizasus.priorizasus.service.CapacityService;
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
class CapacityApiControllerTest {

  @Mock private CapacityService capacityService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new CapacityApiController(capacityService)).build();
  }

  @Test
  @DisplayName("GET /api/slots returns slots for week")
  void getSlotsReturnsList() throws Exception {
    when(capacityService.getSlotsForWeek(any())).thenReturn(List.of());

    mockMvc
        .perform(get("/api/slots"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"));
  }

  @Test
  @DisplayName("GET /api/slots/available returns available slots")
  void getAvailableSlotsReturnsList() throws Exception {
    when(capacityService.getAvailableSlotsForWeek(any())).thenReturn(List.of());

    mockMvc
        .perform(get("/api/slots/available"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"));
  }

  @Test
  @DisplayName("POST /api/slots/create creates weekly slots")
  void createWeeklySlotsReturnsList() throws Exception {
    Slot slot = new Slot();
    slot.setId(1L);
    slot.setStatus(SlotStatus.AVAILABLE);
    when(capacityService.createWeeklySlots(any())).thenReturn(List.of(slot));

    mockMvc
        .perform(post("/api/slots/create"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"));
  }
}
