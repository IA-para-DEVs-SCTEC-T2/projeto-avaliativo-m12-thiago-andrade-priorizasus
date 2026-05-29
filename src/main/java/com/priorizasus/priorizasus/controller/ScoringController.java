package com.priorizasus.priorizasus.controller;

import com.priorizasus.priorizasus.config.ClinicTimeZone;
import com.priorizasus.priorizasus.service.ScoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * MVC controller for Weekly Selection UI — view results and trigger manual execution.
 *
 * <p>Guardrail G2: No {@code @Transactional}. Guardrail G3: No direct repository access. Protected
 * by {@code @PreAuthorize("hasRole('ADMIN')")}.
 */
@Controller
@RequestMapping("/staff")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Staff Web", description = "Painel administrativo — seleção semanal e ranking")
public class ScoringController {

  private static final Logger log = LoggerFactory.getLogger(ScoringController.class);

  private final ScoringService scoringService;
  private final ClinicTimeZone clinicTimeZone;

  public ScoringController(ScoringService scoringService, ClinicTimeZone clinicTimeZone) {
    this.scoringService = scoringService;
    this.clinicTimeZone = clinicTimeZone;
  }

  /** Shows the Weekly Selection result page with ranking tables. */
  @Operation(
      summary = "Resultado da seleção semanal",
      description =
          "Exibe os pacientes selecionados (top 40) e lista de espera. Se nenhuma seleção foi executada, mostra botão para disparar.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de seleção semanal renderizada")
  })
  @GetMapping("/weekly-selection")
  public String weeklySelectionResult(Model model) {
    var result = scoringService.getCurrentSelection();

    model.addAttribute("pageTitle", "Seleção Semanal — PriorizaSUS");
    model.addAttribute("weekStart", clinicTimeZone.today().with(java.time.DayOfWeek.MONDAY));
    model.addAttribute("today", clinicTimeZone.today());
    model.addAttribute("lastExecution", result != null ? "executada" : null);
    model.addAttribute(
        "selected", result != null ? result.selected() : java.util.Collections.emptyList());
    model.addAttribute(
        "waitlistedPreview",
        result != null
            ? result.waitlisted().stream().limit(10).toList()
            : java.util.Collections.emptyList());
    return "staff/weekly-selection-result";
  }

  /**
   * Manually triggers the Weekly Selection.
   *
   * <p>Admin-only. Runs the full selection algorithm: calculates Scores, ranks Patients, selects
   * top 40, generates BookingTokens, and sends email notifications.
   */
  @Operation(
      summary = "Executar seleção semanal",
      description =
          "Dispara a Seleção Semanal manualmente. Calcula Scores, ranqueia pacientes, seleciona top 40, gera BookingTokens.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "302",
        description = "Redireciona para /staff/weekly-selection com mensagem flash"),
    @ApiResponse(
        responseCode = "302",
        description = "Redireciona com mensagem de erro em caso de falha")
  })
  @PostMapping("/weekly-selection/run")
  public String runWeeklySelection(RedirectAttributes redirectAttributes) {
    try {
      var selected = scoringService.executeWeeklySelection();
      redirectAttributes.addFlashAttribute(
          "successMessage",
          "Seleção Semanal concluída! " + selected.size() + " pacientes selecionados.");
    } catch (Exception e) {
      log.error("Weekly Selection failed", e);
      redirectAttributes.addFlashAttribute(
          "errorMessage", "Erro ao executar Seleção Semanal: " + e.getMessage());
    }
    return "redirect:/staff/weekly-selection";
  }
}
