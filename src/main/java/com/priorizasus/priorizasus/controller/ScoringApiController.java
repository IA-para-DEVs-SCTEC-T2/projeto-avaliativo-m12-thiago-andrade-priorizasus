package com.priorizasus.priorizasus.controller;

import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.service.ScoringService;
import com.priorizasus.priorizasus.service.ScoringService.SelectionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller for Weekly Selection — view results and trigger execution.
 *
 * <p>Guardrail G2: No {@code @Transactional}. Guardrail G3: No direct repository access. Guardrail
 * G11: Zero business logic — delegates to {@link ScoringService}.
 *
 * <p>Protected by {@code @PreAuthorize("hasRole('ADMIN')")}.
 */
@RestController
@RequestMapping("/api/scoring")
@PreAuthorize("hasRole('ADMIN')")
@Tag(
    name = "Scoring / Weekly Selection",
    description = "Seleção semanal algorítmica e ranking de pacientes")
public class ScoringApiController {

  private static final Logger log = LoggerFactory.getLogger(ScoringApiController.class);

  private final ScoringService scoringService;

  public ScoringApiController(ScoringService scoringService) {
    this.scoringService = scoringService;
  }

  @Operation(
      summary = "Consultar última seleção semanal",
      description =
          "Retorna o resultado da última Seleção Semanal executada: lista de pacientes"
              + " selecionados (top 40) e lista de espera (waitlisted). Retorna null se"
              + " nenhuma seleção foi executada ainda.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Resultado da seleção (ou null)"),
    @ApiResponse(responseCode = "401", description = "Não autenticado"),
    @ApiResponse(responseCode = "403", description = "Acesso negado — requer role ADMIN")
  })
  @GetMapping("/selection")
  public ResponseEntity<SelectionResult> getSelection() {
    SelectionResult result = scoringService.getCurrentSelection();
    return ResponseEntity.ok(result);
  }

  @Operation(
      summary = "Executar seleção semanal",
      description =
          "Dispara a Seleção Semanal manualmente. Calcula o Score de todos os pacientes"
              + " elegíveis, ranqueia, seleciona os top 40, gera BookingTokens e envia"
              + " emails de notificação. Execução atômica (all-or-nothing).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Seleção executada com sucesso. Retorna contagem e lista de selecionados.",
        content = @Content(schema = @Schema(implementation = Map.class))),
    @ApiResponse(responseCode = "401", description = "Não autenticado"),
    @ApiResponse(responseCode = "403", description = "Acesso negado — requer role ADMIN"),
    @ApiResponse(responseCode = "500", description = "Erro interno durante a seleção")
  })
  @PostMapping("/selection/run")
  public ResponseEntity<?> runSelection() {
    try {
      List<Patient> selected = scoringService.executeWeeklySelection();
      return ResponseEntity.ok(
          Map.of(
              "message",
              "Seleção Semanal concluída!",
              "selectedCount",
              selected.size(),
              "selected",
              selected.stream().map(p -> Map.of("id", p.getId(), "name", p.getName())).toList()));
    } catch (Exception e) {
      log.error("Weekly Selection via API failed", e);
      return ResponseEntity.internalServerError()
          .body(Map.of("error", "Erro ao executar Seleção Semanal: " + e.getMessage()));
    }
  }
}
