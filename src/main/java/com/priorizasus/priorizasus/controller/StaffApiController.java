package com.priorizasus.priorizasus.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller for Staff operations — reports export, audit search, Weekly Selection
 * execution.
 *
 * <p>Guardrail G2: No {@code @Transactional}. Guardrail G3: No direct repository access. Guardrail
 * G11: Zero business logic — delegates to services.
 *
 * <p>Protected by {@code @PreAuthorize("hasRole('ADMIN')")}.
 */
@RestController
@RequestMapping("/api/staff")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Staff Web", description = "Painel administrativo — exportação de relatórios CSV")
public class StaffApiController {

  private static final Logger log = LoggerFactory.getLogger(StaffApiController.class);

  /**
   * Exports reports in CSV format.
   *
   * <p>Supported types: occupancy, coverage, cancellations, audit. Returns CSV with headers and a
   * placeholder row while full service implementation is pending.
   */
  @Operation(
      summary = "Exportar relatório em CSV",
      description =
          "Exporta relatórios administrativos no formato CSV. Tipos suportados: occupancy"
              + " (ocupação semanal), coverage (cobertura por categoria), cancellations"
              + " (cancelamentos), audit (trilha de auditoria).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Arquivo CSV gerado com sucesso",
        content =
            @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary"))),
    @ApiResponse(responseCode = "400", description = "Tipo de relatório inválido"),
    @ApiResponse(responseCode = "401", description = "Não autenticado"),
    @ApiResponse(responseCode = "403", description = "Acesso negado — requer role ADMIN")
  })
  @GetMapping("/reports/export")
  public void exportReport(
      @Parameter(
              description = "Tipo de relatório",
              required = true,
              schema =
                  @Schema(allowableValues = {"occupancy", "coverage", "cancellations", "audit"}))
          @RequestParam
          String type,
      @Parameter(description = "Data de início da semana (yyyy-MM-dd). Ex: 2026-05-25")
          @RequestParam(required = false)
          String weekStart,
      @Parameter(description = "Data inicial do período (yyyy-MM-dd)")
          @RequestParam(required = false)
          String from,
      @Parameter(description = "Data final do período (yyyy-MM-dd)") @RequestParam(required = false)
          String to,
      HttpServletResponse response)
      throws IOException {

    log.info(
        "Report export requested: type={}, weekStart={}, from={}, to={}",
        type,
        weekStart,
        from,
        to);

    response.setContentType("text/csv; charset=UTF-8");
    response.setHeader(
        HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"relatorio-" + type + "-" + LocalDate.now() + ".csv\"");

    PrintWriter writer = response.getWriter();

    switch (type) {
      case "occupancy":
        writer.println("Semana,Total Slots,Agendados,Cancelados,Expirados,Taxa de Utilização (%)");
        writer.println("\"---\",\"40\",\"0\",\"0\",\"0\",\"0\"");
        break;
      case "coverage":
        writer.println("Semana,Categoria,Elegíveis,Agendados,Cobertura (%)");
        writer.println("\"---\",\"Pré-Natal\",\"0\",\"0\",\"0\"");
        writer.println("\"---\",\"Puericultura\",\"0\",\"0\",\"0\"");
        writer.println("\"---\",\"Crônico\",\"0\",\"0\",\"0\"");
        break;
      case "cancellations":
        writer.println("Data,Hora,Paciente,Slot,Motivo");
        writer.println("\"---\",\"---\",\"---\",\"---\",\"---\"");
        break;
      case "audit":
        writer.println("Data/Hora (UTC),Tipo de Ação,Staff,Paciente (ID),Slot (ID),Detalhes");
        writer.println("\"---\",\"---\",\"system\",\"---\",\"---\",\"---\"");
        break;
      default:
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        writer.println("Tipo de relatório inválido: " + type);
        break;
    }

    writer.flush();
  }
}
