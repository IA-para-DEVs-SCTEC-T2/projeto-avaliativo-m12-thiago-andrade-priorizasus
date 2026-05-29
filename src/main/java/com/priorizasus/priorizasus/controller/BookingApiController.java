package com.priorizasus.priorizasus.controller;

import com.priorizasus.priorizasus.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller for Booking operations — patient-initiated appointment cancellation.
 *
 * <p>Guardrail G2: No {@code @Transactional}. Guardrail G3: No direct repository access. Guardrail
 * G11: Zero business logic — delegates to {@link BookingService}.
 *
 * <p>Public endpoint — no authentication required. Validates the 1-day-before rule.
 */
@RestController
@RequestMapping("/api/booking")
@Tag(
    name = "Booking",
    description = "Operações de agendamento via API REST (cancelamento pelo paciente)")
public class BookingApiController {

  private static final Logger log = LoggerFactory.getLogger(BookingApiController.class);

  private final BookingService bookingService;

  public BookingApiController(BookingService bookingService) {
    this.bookingService = bookingService;
  }

  /**
   * Patient-initiated appointment cancellation.
   *
   * <p>Public endpoint — no authentication required. Validates the 1-day-before rule.
   */
  @Operation(
      summary = "Cancelar agendamento (paciente)",
      description =
          "Cancela um agendamento confirmado. Valida a regra de antecedência mínima de 1 dia."
              + " Endpoint público — não requer autenticação.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Agendamento cancelado com sucesso"),
    @ApiResponse(responseCode = "400", description = "Erro de validação — regra de antecedência")
  })
  @PostMapping("/appointments/{appointmentId}/cancel")
  public ResponseEntity<String> cancelAppointment(
      @Parameter(description = "ID do agendamento a ser cancelado", required = true) @PathVariable
          Long appointmentId) {
    try {
      bookingService.cancelAppointment(appointmentId);
      return ResponseEntity.ok("Agendamento cancelado com sucesso.");
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
