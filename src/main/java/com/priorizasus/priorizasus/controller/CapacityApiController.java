package com.priorizasus.priorizasus.controller;

import com.priorizasus.priorizasus.entity.Slot;
import com.priorizasus.priorizasus.service.CapacityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller for Slot grid and capacity queries.
 *
 * <p>Guardrail G2: No {@code @Transactional}. Guardrail G3: No direct repository access. Guardrail
 * G11: Zero business logic — delegates to {@link CapacityService}.
 */
@RestController
@RequestMapping("/api/slots")
@Tag(name = "Slots / Capacity", description = "Consulta de horários e capacidade semanal")
public class CapacityApiController {

  private static final Logger log = LoggerFactory.getLogger(CapacityApiController.class);

  private final CapacityService capacityService;

  public CapacityApiController(CapacityService capacityService) {
    this.capacityService = capacityService;
  }

  @Operation(
      summary = "Listar slots da semana",
      description =
          "Retorna todos os slots (horários) de uma semana. Cada slot tem 30 minutos,"
              + " segunda a sexta, 08:00–11:30. Se weekStart não informado, usa a segunda-feira"
              + " da semana atual.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Lista de slots retornada com sucesso")
  })
  @GetMapping
  public ResponseEntity<List<Slot>> getSlots(
      @Parameter(description = "Data de início da semana (yyyy-MM-dd). Ex: 2026-05-25")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate weekStart) {
    if (weekStart == null) {
      weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
    }
    List<Slot> slots = capacityService.getSlotsForWeek(weekStart);
    return ResponseEntity.ok(slots);
  }

  @Operation(
      summary = "Listar slots disponíveis",
      description =
          "Retorna apenas os slots com status AVAILABLE (não reservados nem agendados)"
              + " para a semana informada.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Lista de slots disponíveis retornada")
  })
  @GetMapping("/available")
  public ResponseEntity<List<Slot>> getAvailableSlots(
      @Parameter(description = "Data de início da semana (yyyy-MM-dd)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate weekStart) {
    if (weekStart == null) {
      weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
    }
    List<Slot> slots = capacityService.getAvailableSlotsForWeek(weekStart);
    return ResponseEntity.ok(slots);
  }

  @Operation(
      summary = "Criar slots da semana",
      description =
          "Cria os 40 slots AVAILABLE para a semana informada (8 por dia × 5 dias)."
              + " Idempotente: se os slots já existirem, retorna os existentes.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Slots criados (ou já existentes) retornados")
  })
  @PostMapping("/create")
  public ResponseEntity<List<Slot>> createWeeklySlots(
      @Parameter(description = "Data de início da semana (yyyy-MM-dd). Padrão: segunda atual.")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate weekStart) {
    if (weekStart == null) {
      weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
    }
    List<Slot> slots = capacityService.createWeeklySlots(weekStart);
    return ResponseEntity.ok(slots);
  }
}
