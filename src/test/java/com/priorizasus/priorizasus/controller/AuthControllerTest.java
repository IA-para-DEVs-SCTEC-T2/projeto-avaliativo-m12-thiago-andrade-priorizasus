package com.priorizasus.priorizasus.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new AuthController()).build();
  }

  @Test
  @DisplayName("GET /auth/login returns login page")
  void loginPageReturnsLoginView() throws Exception {
    mockMvc
        .perform(get("/auth/login"))
        .andExpect(status().isOk())
        .andExpect(view().name("auth/login"))
        .andExpect(model().attributeExists("pageTitle"));
  }
}
