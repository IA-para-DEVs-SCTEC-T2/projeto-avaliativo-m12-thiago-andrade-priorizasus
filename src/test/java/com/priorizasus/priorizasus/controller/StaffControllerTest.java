package com.priorizasus.priorizasus.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.priorizasus.priorizasus.config.ClinicTimeZone;
import com.priorizasus.priorizasus.entity.AppointmentStatus;
import com.priorizasus.priorizasus.service.AppointmentService;
import com.priorizasus.priorizasus.service.AuditLogService;
import com.priorizasus.priorizasus.service.CapacityService;
import com.priorizasus.priorizasus.service.PatientService;
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
class StaffControllerTest {

  @Mock private PatientService patientService;
  @Mock private CapacityService capacityService;
  @Mock private AppointmentService appointmentService;
  @Mock private ScoringService scoringService;
  @Mock private ClinicTimeZone clinicTimeZone;
  @Mock private AuditLogService auditLogService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new StaffController(
                    patientService,
                    capacityService,
                    appointmentService,
                    scoringService,
                    clinicTimeZone,
                    auditLogService))
            .build();
  }

  @Test
  @DisplayName("GET /staff/dashboard shows dashboard")
  void dashboardShowsPage() throws Exception {
    when(clinicTimeZone.today()).thenReturn(LocalDate.of(2026, 5, 25));
    when(capacityService.getSlotsForWeek(any())).thenReturn(List.of());
    when(appointmentService.findByWeekStartAndStatus(any(), eq(AppointmentStatus.CONFIRMED)))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/staff/dashboard"))
        .andExpect(status().isOk())
        .andExpect(view().name("staff/dashboard"))
        .andExpect(model().attributeExists("slots"))
        .andExpect(model().attribute("totalSlots", 40));
  }

  @Test
  @DisplayName("GET /staff/occupancy shows occupancy")
  void occupancyShowsPage() throws Exception {
    when(clinicTimeZone.today()).thenReturn(LocalDate.of(2026, 5, 25));
    when(capacityService.getSlotsForWeek(any())).thenReturn(List.of());
    when(appointmentService.findByWeekStartAndStatus(any(), eq(AppointmentStatus.CONFIRMED)))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/staff/occupancy"))
        .andExpect(status().isOk())
        .andExpect(view().name("staff/occupancy"))
        .andExpect(model().attributeExists("prenatalCount"))
        .andExpect(model().attributeExists("childCount"))
        .andExpect(model().attributeExists("chronicCount"));
  }

  @Test
  @DisplayName("GET /staff/audit-log shows audit page")
  void auditLogShowsPage() throws Exception {
    when(auditLogService.findAll()).thenReturn(List.of());

    mockMvc
        .perform(get("/staff/audit-log"))
        .andExpect(status().isOk())
        .andExpect(view().name("staff/audit-log"))
        .andExpect(model().attributeExists("auditEntries"));
  }

  @Test
  @DisplayName("GET /staff/audit-log with actionType filter")
  void auditLogWithActionTypeFilter() throws Exception {
    when(auditLogService.findByActionType(any())).thenReturn(List.of());

    mockMvc
        .perform(get("/staff/audit-log").param("actionType", "BOOKING"))
        .andExpect(status().isOk())
        .andExpect(view().name("staff/audit-log"));
  }

  @Test
  @DisplayName("GET /staff/audit-log with date range")
  void auditLogWithDateRange() throws Exception {
    when(auditLogService.findByTimestampBetween(any(), any())).thenReturn(List.of());

    mockMvc
        .perform(get("/staff/audit-log").param("from", "2026-05-01").param("to", "2026-05-31"))
        .andExpect(status().isOk())
        .andExpect(view().name("staff/audit-log"));
  }

  @Test
  @DisplayName("GET /staff/reports shows reports page")
  void reportsShowsPage() throws Exception {
    when(clinicTimeZone.today()).thenReturn(LocalDate.of(2026, 5, 25));

    mockMvc
        .perform(get("/staff/reports"))
        .andExpect(status().isOk())
        .andExpect(view().name("staff/reports"))
        .andExpect(model().attributeExists("weekStartIso"));
  }

  @Test
  @DisplayName("GET /staff/system-health shows health page")
  void systemHealthShowsPage() throws Exception {
    when(clinicTimeZone.today()).thenReturn(LocalDate.of(2026, 5, 28));
    when(clinicTimeZone.formatForDisplay(any())).thenReturn("28/05/2026 15:30");

    mockMvc
        .perform(get("/staff/system-health"))
        .andExpect(status().isOk())
        .andExpect(view().name("staff/system-health"))
        .andExpect(model().attributeExists("brtTimeNow"));
  }

  @Test
  @DisplayName("GET /staff/patients redirects to /patients")
  void staffPatientsRedirects() throws Exception {
    mockMvc
        .perform(get("/staff/patients"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/patients"));
  }
}
