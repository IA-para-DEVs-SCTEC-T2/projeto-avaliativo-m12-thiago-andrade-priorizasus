package com.priorizasus.priorizasus.controller;

import com.priorizasus.priorizasus.config.ClinicTimeZone;
import com.priorizasus.priorizasus.entity.Patient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Home page — patient registration form for the Weekly Selection. */
@Controller
@Tag(name = "Home", description = "Página inicial e cadastro público de pacientes")
public class HomeController {

  private final ClinicTimeZone clinicTimeZone;

  public HomeController(ClinicTimeZone clinicTimeZone) {
    this.clinicTimeZone = clinicTimeZone;
  }

  @Operation(
      summary = "Página inicial",
      description = "Exibe o formulário de cadastro público para a Seleção Semanal.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página renderizada com sucesso")
  })
  @GetMapping("/")
  public String home(Model model) {
    java.time.LocalDate today = clinicTimeZone.today();
    java.time.LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
    java.time.format.DateTimeFormatter displayFmt =
        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    model.addAttribute("patient", new Patient());
    model.addAttribute("pageTitle", "PRIORIZASUS");
    model.addAttribute("today", today);
    model.addAttribute("weekStart", weekStart);
    model.addAttribute("weekStartDisplay", weekStart.format(displayFmt));
    return "index";
  }
}
