package com.priorizasus.priorizasus.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.priorizasus.priorizasus.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BookingApiControllerTest {

  @Mock private BookingService bookingService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new BookingApiController(bookingService)).build();
  }

  @Test
  @DisplayName("POST /api/booking/appointments/{id}/cancel succeeds")
  void cancelReturnsOk() throws Exception {
    doNothing().when(bookingService).cancelAppointment(anyLong());

    mockMvc
        .perform(post("/api/booking/appointments/1/cancel"))
        .andExpect(status().isOk())
        .andExpect(content().string("Agendamento cancelado com sucesso."));
  }

  @Test
  @DisplayName("POST /api/booking/appointments/{id}/cancel returns 400 on error")
  void cancelReturnsBadRequestWhenInvalid() throws Exception {
    doThrow(new IllegalStateException("1-day rule violated"))
        .when(bookingService)
        .cancelAppointment(anyLong());

    mockMvc
        .perform(post("/api/booking/appointments/1/cancel"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("1-day rule violated"));
  }
}
