package com.priorizasus.priorizasus.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.priorizasus.priorizasus.config.ClinicTimeZone;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class HomeControllerTest {

  @Mock private ClinicTimeZone clinicTimeZone;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new HomeController(clinicTimeZone)).build();
  }

  @Test
  @DisplayName("GET / returns index with model attributes")
  void homeReturnsIndexWithAttributes() throws Exception {
    when(clinicTimeZone.today()).thenReturn(LocalDate.of(2026, 5, 28));

    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(view().name("index"))
        .andExpect(model().attributeExists("patient"))
        .andExpect(model().attributeExists("pageTitle"))
        .andExpect(model().attributeExists("today"))
        .andExpect(model().attributeExists("weekStart"))
        .andExpect(model().attributeExists("weekStartDisplay"));
  }
}
