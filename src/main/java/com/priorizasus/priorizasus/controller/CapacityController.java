package com.priorizasus.priorizasus.controller;

import com.priorizasus.priorizasus.config.ClinicTimeZone;
import com.priorizasus.priorizasus.entity.Slot;
import com.priorizasus.priorizasus.service.CapacityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
 * MVC controller for Slot grid and capacity queries.
 *
 * <p>Guardrail G2: No {@code @Transactional}. Guardrail G3: No direct repository access. Guardrail
 * G11: Zero business logic.
 */
@Controller
@RequestMapping("/staff")
@PreAuthorize("hasRole('ADMIN')")
@Tag(
    name = "Staff Web",
    description = "Painel administrativo — grade de horários, ocupação e capacidade")
public class CapacityController {

  private static final Logger log = LoggerFactory.getLogger(CapacityController.class);

  private final CapacityService capacityService;
  private final ClinicTimeZone clinicTimeZone;

  public CapacityController(CapacityService capacityService, ClinicTimeZone clinicTimeZone) {
    this.capacityService = capacityService;
    this.clinicTimeZone = clinicTimeZone;
  }

  /** Shows the weekly slot grid. */
  @Operation(
      summary = "Grade de horários semanal",
      description = "Exibe a grade de slots de Seg–Sex, 08:00–11:30, com navegação entre semanas.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Grade de horários renderizada")})
  @GetMapping("/capacity")
  public String slotGrid(@RequestParam(required = false) String weekStartStr, Model model) {

    LocalDate weekStart =
        (weekStartStr != null)
            ? LocalDate.parse(weekStartStr)
            : clinicTimeZone.today().with(java.time.DayOfWeek.MONDAY);

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

    List<Slot> slots = new ArrayList<>();
    try {
      slots = capacityService.getSlotsForWeek(weekStart);
    } catch (UnsupportedOperationException e) {
      log.debug("CapacityService not yet implemented — showing empty grid");
    }

    model.addAttribute("slots", slots);
    model.addAttribute("weekStart", weekStart);
    model.addAttribute("weekStartDisplay", weekStart.format(displayFmt));
    model.addAttribute("dayHeaders", dayHeaders);
    model.addAttribute("timeHeaders", timeHeaders);
    model.addAttribute("showLinks", true);
    model.addAttribute("pageTitle", "Grade de Horários — PRIORIZASUS");
    return "staff/slot-grid";
  }
}
