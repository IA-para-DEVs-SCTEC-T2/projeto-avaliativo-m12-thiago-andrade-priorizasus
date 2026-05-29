package com.priorizasus.priorizasus.controller;

import com.priorizasus.priorizasus.config.ClinicTimeZone;
import com.priorizasus.priorizasus.entity.Appointment;
import com.priorizasus.priorizasus.entity.AppointmentStatus;
import com.priorizasus.priorizasus.entity.AuditActionType;
import com.priorizasus.priorizasus.entity.AuditLog;
import com.priorizasus.priorizasus.entity.Category;
import com.priorizasus.priorizasus.entity.CategoryType;
import com.priorizasus.priorizasus.entity.Slot;
import com.priorizasus.priorizasus.service.AppointmentService;
import com.priorizasus.priorizasus.service.AuditLogService;
import com.priorizasus.priorizasus.service.CapacityService;
import com.priorizasus.priorizasus.service.PatientService;
import com.priorizasus.priorizasus.service.ScoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * MVC controller for the Staff Dashboard — the main control panel.
 *
 * <p>Guardrail G2: No {@code @Transactional}. Guardrail G3: No direct repository access. Guardrail
 * G11: Zero business logic — delegates to services.
 *
 * <p>Protected by {@code @PreAuthorize("hasRole('ADMIN')")} — only authenticated admin users may
 * access staff pages.
 */
@Controller
@RequestMapping("/staff")
@PreAuthorize("hasRole('ADMIN')")
@Tag(
    name = "Staff Web",
    description = "Painel administrativo — dashboard principal e visão de ocupação")
public class StaffController {

  private static final Logger log = LoggerFactory.getLogger(StaffController.class);

  private final PatientService patientService;
  private final CapacityService capacityService;
  private final AppointmentService appointmentService;
  private final ScoringService scoringService;
  private final ClinicTimeZone clinicTimeZone;
  private final AuditLogService auditLogService;

  public StaffController(
      PatientService patientService,
      CapacityService capacityService,
      AppointmentService appointmentService,
      ScoringService scoringService,
      ClinicTimeZone clinicTimeZone,
      AuditLogService auditLogService) {
    this.patientService = patientService;
    this.capacityService = capacityService;
    this.appointmentService = appointmentService;
    this.scoringService = scoringService;
    this.clinicTimeZone = clinicTimeZone;
    this.auditLogService = auditLogService;
  }

  /** Main staff dashboard — composite page with fragments. */
  @Operation(
      summary = "Dashboard principal",
      description =
          "Painel administrativo com grade de horários, minicards de ocupação por categoria e acesso à Seleção Semanal.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Dashboard renderizado com dados da semana atual")
  })
  @GetMapping("/dashboard")
  public String dashboard(Model model) {
    LocalDate weekStart = clinicTimeZone.today().with(java.time.DayOfWeek.MONDAY);
    DateTimeFormatter displayFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd/MM");

    List<Map<String, String>> dayHeaders = new ArrayList<>();
    String[] dayNames = {"Seg", "Ter", "Qua", "Qui", "Sex"};
    for (int i = 0; i < 5; i++) {
      LocalDate day = weekStart.plusDays(i);
      Map<String, String> header = new LinkedHashMap<>();
      header.put("dayName", dayNames[i]);
      header.put("dayDate", day.format(dayFmt));
      dayHeaders.add(header);
    }

    List<String> timeHeaders = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      int totalMinutes = 8 * 60 + i * 30;
      timeHeaders.add(String.format("%02d:%02d", totalMinutes / 60, totalMinutes % 60));
    }

    model.addAttribute("pageTitle", "Dashboard — PRIORIZASUS");
    model.addAttribute("weekStart", weekStart);
    model.addAttribute("weekStartDisplay", weekStart.format(displayFmt));
    model.addAttribute("dayHeaders", dayHeaders);
    model.addAttribute("timeHeaders", timeHeaders);
    model.addAttribute("today", clinicTimeZone.today());
    List<Slot> slots = capacityService.getSlotsForWeek(weekStart);
    java.util.Map<Long, String> slotColors = new java.util.LinkedHashMap<>();
    for (Slot s : slots) {
      if (s.getPatient() != null) {
        patientService.hydrateCategories(s.getPatient());
        String color = "bg-warning text-dark";
        for (var cat : s.getPatient().getCategories()) {
          if (cat.getType().name().equals("PRENATAL")) {
            color = "bg-danger";
            break;
          } else if (cat.getType().name().equals("CHILD")) {
            color = "bg-info";
          }
        }
        slotColors.put(s.getId(), color);
      }
    }
    model.addAttribute("slots", slots);
    model.addAttribute("slotColors", slotColors);
    model.addAttribute("totalSlots", 40);
    model.addAttribute("showLinks", true);

    List<Appointment> appointments =
        appointmentService.findByWeekStartAndStatus(weekStart, AppointmentStatus.CONFIRMED);

    // Build category labels for each booked slot
    java.util.Map<Long, String> slotCategoryLabels = new java.util.LinkedHashMap<>();
    CategoryCounts categoryCounts = countCategories(appointments, slotCategoryLabels);
    model.addAttribute("slotCategoryLabels", slotCategoryLabels);
    model.addAttribute("prenatalCount", categoryCounts.prenatalCount());
    model.addAttribute("childCount", categoryCounts.childCount());
    model.addAttribute("chronicCount", categoryCounts.chronicCount());
    model.addAttribute("bookedCount", categoryCounts.bookedCount());
    int occupancyPct = slots.isEmpty() ? 0 : (categoryCounts.bookedCount() * 100) / slots.size();
    model.addAttribute("occupancyPct", occupancyPct);

    return "staff/dashboard";
  }

  /** Detailed occupancy view with category breakdown. */
  @Operation(
      summary = "Ocupação detalhada",
      description =
          "Visão de ocupação semanal com percentual de utilização e breakdown por categoria (Pré-Natal, Puericultura, Crônico).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de ocupação renderizada")
  })
  @GetMapping("/occupancy")
  public String occupancy(@RequestParam(required = false) String weekStartStr, Model model) {
    LocalDate weekStart =
        (weekStartStr != null)
            ? LocalDate.parse(weekStartStr)
            : clinicTimeZone.today().with(java.time.DayOfWeek.MONDAY);
    DateTimeFormatter displayFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    List<Slot> slots = capacityService.getSlotsForWeek(weekStart);
    int total = slots.size();
    List<Appointment> appointments =
        appointmentService.findByWeekStartAndStatus(weekStart, AppointmentStatus.CONFIRMED);
    CategoryCounts categoryCounts = countCategories(appointments, new java.util.LinkedHashMap<>());

    log.info("Occupancy: {} slots found for week {}", total, weekStart);

    log.info(
        "Occupancy: prenatal={}, child={}, chronic={}",
        categoryCounts.prenatalCount(),
        categoryCounts.childCount(),
        categoryCounts.chronicCount());
    int occupancyPct = total > 0 ? (categoryCounts.bookedCount() * 100) / total : 0;

    model.addAttribute("pageTitle", "Ocupação — PRIORIZASUS");
    model.addAttribute("weekStart", weekStart);
    model.addAttribute("weekStartDisplay", weekStart.format(displayFmt));
    model.addAttribute("today", clinicTimeZone.today());
    model.addAttribute("slots", slots);
    model.addAttribute("totalSlots", total);
    model.addAttribute("bookedSlots", categoryCounts.bookedCount());
    model.addAttribute("prenatalCount", categoryCounts.prenatalCount());
    model.addAttribute("childCount", categoryCounts.childCount());
    model.addAttribute("chronicCount", categoryCounts.chronicCount());
    model.addAttribute("occupancyPct", occupancyPct);
    return "staff/occupancy";
  }

  private CategoryCounts countCategories(
      List<Appointment> appointments, Map<Long, String> slotCategoryLabels) {
    int prenatalCount = 0;
    int childCount = 0;
    int chronicCount = 0;
    int bookedCount = 0;

    for (Appointment appointment : appointments) {
      if (appointment.getPatient() == null || appointment.getSlot() == null) {
        continue;
      }

      bookedCount++;
      patientService.hydrateCategories(appointment.getPatient());
      for (Category category : appointment.getPatient().getCategories()) {
        if (category.getType() == CategoryType.PRENATAL) {
          prenatalCount++;
          if (!slotCategoryLabels.containsKey(appointment.getSlot().getId())) {
            slotCategoryLabels.put(appointment.getSlot().getId(), "Pré-Natal");
          }
        } else if (category.getType() == CategoryType.CHILD) {
          childCount++;
          if (!slotCategoryLabels.containsKey(appointment.getSlot().getId())) {
            slotCategoryLabels.put(appointment.getSlot().getId(), "Puericultura");
          }
        } else if (category.getType() == CategoryType.CHRONIC) {
          chronicCount++;
          if (!slotCategoryLabels.containsKey(appointment.getSlot().getId())) {
            slotCategoryLabels.put(appointment.getSlot().getId(), "Crônico");
          }
        }
      }
    }

    return new CategoryCounts(prenatalCount, childCount, chronicCount, bookedCount);
  }

  private record CategoryCounts(
      int prenatalCount, int childCount, int chronicCount, int bookedCount) {}

  /** Audit log viewer with filters. */
  @GetMapping("/audit-log")
  public String auditLog(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String actionType,
      Model model) {
    List<AuditActionType> actionTypes = Arrays.asList(AuditActionType.values());
    List<AuditLog> auditEntries;

    try {
      if (actionType != null && !actionType.isBlank()) {
        AuditActionType type = AuditActionType.valueOf(actionType);
        if (from != null && !from.isBlank() && to != null && !to.isBlank()) {
          Instant fromInstant = Instant.parse(from + "T00:00:00Z");
          Instant toInstant = Instant.parse(to + "T23:59:59Z");
          auditEntries =
              auditLogService.findByActionTypeAndTimestampBetween(type, fromInstant, toInstant);
        } else {
          auditEntries = auditLogService.findByActionType(type);
        }
      } else if (from != null && !from.isBlank() && to != null && !to.isBlank()) {
        Instant fromInstant = Instant.parse(from + "T00:00:00Z");
        Instant toInstant = Instant.parse(to + "T23:59:59Z");
        auditEntries = auditLogService.findByTimestampBetween(fromInstant, toInstant);
      } else {
        auditEntries = auditLogService.findAll();
      }
    } catch (Exception e) {
      log.warn("Error querying audit log", e);
      auditEntries = new ArrayList<>();
    }

    model.addAttribute("pageTitle", "Auditoria — PRIORIZASUS");
    model.addAttribute("actionTypes", actionTypes);
    model.addAttribute("auditEntries", auditEntries != null ? auditEntries : new ArrayList<>());
    return "staff/audit-log";
  }

  /** Reports export page. */
  @GetMapping("/reports")
  public String reports(Model model) {
    LocalDate weekStart = clinicTimeZone.today().with(java.time.DayOfWeek.MONDAY);
    DateTimeFormatter isoFmt = DateTimeFormatter.ISO_LOCAL_DATE;

    model.addAttribute("pageTitle", "Relatórios — PRIORIZASUS");
    model.addAttribute("today", clinicTimeZone.today());
    model.addAttribute("weekStartIso", weekStart.format(isoFmt));
    return "staff/reports";
  }

  /** System health dashboard. */
  @GetMapping("/system-health")
  public String systemHealth(Model model) {
    model.addAttribute("pageTitle", "Saúde do Sistema — PRIORIZASUS");
    model.addAttribute("today", clinicTimeZone.today());
    model.addAttribute("brtTimeNow", clinicTimeZone.formatForDisplay(java.time.Instant.now()));
    return "staff/system-health";
  }

  /** Redirects /staff/patients to /patients (patient management). */
  @GetMapping("/patients")
  public String staffPatients() {
    return "redirect:/patients";
  }
}
