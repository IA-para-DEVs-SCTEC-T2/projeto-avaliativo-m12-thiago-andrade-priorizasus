package com.priorizasus.priorizasus.controller;

import com.priorizasus.priorizasus.config.ClinicTimeZone;
import com.priorizasus.priorizasus.entity.Appointment;
import com.priorizasus.priorizasus.entity.AppointmentStatus;
import com.priorizasus.priorizasus.entity.BookingToken;
import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.entity.Slot;
import com.priorizasus.priorizasus.service.AppointmentService;
import com.priorizasus.priorizasus.service.BookingService;
import com.priorizasus.priorizasus.service.CapacityService;
import com.priorizasus.priorizasus.service.PatientService;
import com.priorizasus.priorizasus.service.ScoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * MVC controller for the Patient booking portal — CPF-based lookup and token-based slot selection.
 *
 * <p>Guardrail G2: No {@code @Transactional}. Guardrail G3: No direct repository access. Guardrail
 * G11: Zero business logic. Guardrail G13: Redirect-after-POST.
 */
@Controller
@RequestMapping("/booking")
@Tag(
    name = "Booking Web",
    description =
        "Portal de agendamento do paciente — consulta por CPF e seleção de horário (Thymeleaf)")
public class BookingController {

  private static final Logger log = LoggerFactory.getLogger(BookingController.class);

  private final PatientService patientService;
  private final CapacityService capacityService;
  private final AppointmentService appointmentService;
  private final BookingService bookingService;
  private final ClinicTimeZone clinicTimeZone;
  private final ScoringService scoringService;

  public BookingController(
      PatientService patientService,
      CapacityService capacityService,
      AppointmentService appointmentService,
      BookingService bookingService,
      ClinicTimeZone clinicTimeZone,
      ScoringService scoringService) {
    this.patientService = patientService;
    this.capacityService = capacityService;
    this.appointmentService = appointmentService;
    this.bookingService = bookingService;
    this.clinicTimeZone = clinicTimeZone;
    this.scoringService = scoringService;
  }

  /** Shows the CPF lookup form. */
  @Operation(
      summary = "Formulário de consulta por CPF",
      description =
          "Exibe o formulário onde o paciente digita o CPF para buscar seus agendamentos.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Formulário renderizado")})
  @GetMapping("/lookup")
  public String lookupForm(Model model) {
    model.addAttribute("pageTitle", "Agendamento — PRIORIZASUS");
    return "booking/lookup";
  }

  /** Processes CPF lookup. Guardrail G13: Redirect-after-POST. */
  @Operation(
      summary = "Processar consulta de CPF",
      description =
          "Valida o CPF informado e redireciona para o dashboard do paciente ou para seleção de horário via token.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "302",
        description = "Redireciona para /booking/{patientId} ou /booking/select/{token}"),
    @ApiResponse(
        responseCode = "302",
        description =
            "Redireciona para /booking/lookup com mensagem de erro se CPF inválido ou não encontrado")
  })
  @PostMapping("/lookup")
  public String lookupCpf(@RequestParam String cpf, RedirectAttributes redirectAttributes) {

    if (cpf == null || cpf.replaceAll("\\D", "").length() != 11) {
      redirectAttributes.addFlashAttribute("errorMessage", "CPF inválido. Digite 11 dígitos.");
      return "redirect:/booking/lookup";
    }

    String cleanCpf = cpf.replaceAll("\\D", "");
    try {
      Optional<Patient> patient = patientService.findByCpf(cleanCpf);
      if (patient.isPresent()) {
        Optional<com.priorizasus.priorizasus.entity.BookingToken> token =
            bookingService.findActiveTokenForPatient(patient.get().getId());
        if (token.isPresent()) {
          return "redirect:/booking/select/" + token.get().getToken();
        }
        redirectAttributes.addFlashAttribute(
            "infoMessage",
            "Seu cadastro foi encontrado. Verifique sua tela de agendamento para continuar.");
        return "redirect:/booking/" + patient.get().getId();
      }
      redirectAttributes.addFlashAttribute("errorMessage", "CPF não encontrado.");
    } catch (UnsupportedOperationException e) {
      redirectAttributes.addFlashAttribute("errorMessage", "Serviço de pacientes indisponível.");
    }
    return "redirect:/booking/lookup";
  }

  /** Shows the patient booking dashboard with reserved slots and appointments. */
  @Operation(
      summary = "Dashboard de agendamento do paciente",
      description =
          "Exibe os horários disponíveis, agendamentos confirmados e status de seleção semanal do paciente.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Dashboard renderizado"),
    @ApiResponse(responseCode = "200", description = "Página de erro se paciente não encontrado")
  })
  @GetMapping("/{patientId}")
  public String patientDashboard(@PathVariable Long patientId, Model model) {
    Optional<Patient> patient = Optional.empty();
    try {
      patient = patientService.findById(patientId);
    } catch (UnsupportedOperationException e) {
      log.debug("PatientService not yet implemented");
    }

    if (patient.isEmpty()) {
      model.addAttribute("errorMessage", "Paciente não encontrado.");
      return "error";
    }

    Patient p = patient.get();
    model.addAttribute("patient", p);
    model.addAttribute("pageTitle", p.getName() + " — Agendamento — PRIORIZASUS");
    model.addAttribute("today", clinicTimeZone.today());

    BookingToken bookingToken = null;
    try {
      bookingToken = bookingService.findActiveTokenForPatient(patientId).orElse(null);
    } catch (Exception e) {
      log.debug("Error finding active booking token: {}", e.getMessage());
    }

    boolean selectedForWeek = bookingToken != null || scoringService.isPatientSelected(patientId);

    model.addAttribute("hasBookingLink", bookingToken != null);
    model.addAttribute("selectedForWeek", selectedForWeek);
    if (bookingToken != null) {
      model.addAttribute("token", bookingToken.getToken());
      model.addAttribute("weekStart", bookingToken.getWeekStart());
    } else {
      model.addAttribute("weekStart", clinicTimeZone.today().with(java.time.DayOfWeek.MONDAY));
    }

    LocalDate weekStart = (LocalDate) model.getAttribute("weekStart");
    model.addAttribute("weekEnd", weekStart.plusDays(4));
    try {
      capacityService.createWeeklySlots(weekStart);
    } catch (Exception e) {
      log.debug("Error ensuring weekly slots exist: {}", e.getMessage());
    }

    // Get slots available for direct booking only when the patient is selected this week
    List<Slot> availableSlots = new ArrayList<>();
    if (selectedForWeek) {
      try {
        availableSlots = capacityService.getAvailableSlotsForWeek(weekStart);
      } catch (Exception e) {
        log.debug("Error finding available slots: {}", e.getMessage());
      }
    }

    availableSlots.sort(
        Comparator.comparing(
            Slot::getSlotDateTime, Comparator.nullsLast(Comparator.naturalOrder())));
    model.addAttribute("availableSlots", availableSlots);
    model.addAttribute("availableSlotsByDay", groupSlotsByDay(availableSlots));

    // Get confirmed appointments
    List<Appointment> appointments = new ArrayList<>();
    try {
      appointments = appointmentService.findByPatientId(patientId);
    } catch (Exception e) {
      log.debug("Error finding appointments: {}", e.getMessage());
    }
    model.addAttribute("appointments", appointments);
    boolean hasConfirmedAppointmentThisWeek =
        appointments.stream()
            .anyMatch(
                appointment ->
                    appointment.getStatus() == AppointmentStatus.CONFIRMED
                        && weekStart.equals(appointment.getWeekStart()));
    model.addAttribute("hasConfirmedAppointmentThisWeek", hasConfirmedAppointmentThisWeek);

    return "booking/dashboard";
  }

  /**
   * Token-based slot selection page — the landing page from the email link.
   *
   * <p>Validates the token, then displays all AVAILABLE Slots for the target Week. The Patient
   * picks their preferred time.
   */
  @Operation(
      summary = "Seleção de horário via token",
      description =
          "Valida o token de agendamento e exibe os slots disponíveis para a semana alvo. Página acessada pelo link do email.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de seleção de horário renderizada"),
    @ApiResponse(responseCode = "200", description = "Página de erro se token inválido ou expirado")
  })
  @GetMapping("/select/{token}")
  public String selectSlot(@PathVariable String token, Model model) {
    try {
      Patient patient = bookingService.validateToken(token);

      // Use the token's target week (the week the selection was run for)
      LocalDate weekStart = bookingService.getWeekStartForToken(token);

      // Ensure slots exist for this week
      try {
        capacityService.createWeeklySlots(weekStart);
      } catch (Exception e) {
        log.debug("Error ensuring weekly slots exist: {}", e.getMessage());
      }

      List<Slot> slots = new ArrayList<>();
      try {
        slots = capacityService.getAvailableSlotsForWeek(weekStart);
      } catch (UnsupportedOperationException e) {
        log.debug("CapacityService stub — no slots available");
      }

      model.addAttribute("patient", patient);
      model.addAttribute("token", token);
      model.addAttribute("slots", slots);
      model.addAttribute("availableSlotsByDay", groupSlotsByDay(slots));
      model.addAttribute("weekStart", weekStart);
      model.addAttribute("weekEnd", weekStart.plusDays(4));
      model.addAttribute("pageTitle", "Escolha seu Horário — PRIORIZASUS");
      model.addAttribute("today", clinicTimeZone.today());
      return "booking/select-slot";
    } catch (IllegalStateException e) {
      model.addAttribute("errorMessage", e.getMessage());
      return "error";
    }
  }

  /** Confirms a Slot booking via token. Guardrail G13: Redirect-after-POST. */
  @Operation(
      summary = "Confirmar agendamento via token",
      description =
          "Reserva o slot selecionado para o paciente vinculado ao token. Redireciona para página de confirmação.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "302",
        description = "Redireciona para /booking/confirmation em caso de sucesso"),
    @ApiResponse(
        responseCode = "302",
        description = "Redireciona para /booking/select/{token} com erro se slot já ocupado")
  })
  @PostMapping("/select/{token}")
  public String confirmBooking(
      @PathVariable String token,
      @RequestParam Long slotId,
      RedirectAttributes redirectAttributes) {
    try {
      bookingService.reserveSlot(token, slotId);
      redirectAttributes.addFlashAttribute("successMessage", "Agendamento confirmado com sucesso!");
      return "redirect:/booking/confirmation";
    } catch (IllegalStateException e) {
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
      return "redirect:/booking/select/" + token;
    }
  }

  /** Confirmation page shown after successful booking. */
  @Operation(
      summary = "Página de confirmação",
      description =
          "Exibe a mensagem de agendamento confirmado com instruções para o dia da consulta.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de confirmação renderizada")
  })
  @GetMapping("/confirmation")
  public String confirmation(Model model) {
    model.addAttribute("pageTitle", "Agendamento Confirmado — PRIORIZASUS");
    return "booking/confirmation";
  }

  /** Confirms a reserved slot from the patient dashboard. */
  @Operation(
      summary = "Confirmar slot reservado",
      description =
          "Confirma um slot que estava em estado RESERVED para o paciente, transformando em agendamento BOOKED.")
  @ApiResponses({
    @ApiResponse(responseCode = "302", description = "Redireciona para /booking/{patientId}")
  })
  @PostMapping("/{patientId}/confirm")
  public String confirmReservedSlot(
      @PathVariable Long patientId,
      @RequestParam Long slotId,
      RedirectAttributes redirectAttributes) {
    try {
      bookingService.confirmReservedSlot(patientId, slotId);
      redirectAttributes.addFlashAttribute("successMessage", "Horário confirmado com sucesso!");
    } catch (IllegalStateException e) {
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/booking/" + patientId;
  }

  /** Confirms a direct booking from the CPF lookup dashboard when no email token is available. */
  @Operation(
      summary = "Agendamento direto (sem token)",
      description =
          "Reserva um slot diretamente para o paciente via dashboard de CPF, sem uso de token de email.")
  @ApiResponses({
    @ApiResponse(responseCode = "302", description = "Redireciona para /booking/{patientId}")
  })
  @PostMapping("/{patientId}/direct")
  public String directBookingFromDashboard(
      @PathVariable Long patientId,
      @RequestParam Long slotId,
      RedirectAttributes redirectAttributes) {
    try {
      bookingService.reserveSlotForPatient(patientId, slotId);
      redirectAttributes.addFlashAttribute("successMessage", "Agendamento confirmado com sucesso!");
    } catch (IllegalStateException e) {
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/booking/" + patientId;
  }

  /** Cancels an appointment from the patient dashboard. */
  @Operation(
      summary = "Cancelar agendamento (dashboard)",
      description =
          "Cancela um agendamento confirmado a partir do dashboard do paciente. Valida regra de antecedência de 1 dia.")
  @ApiResponses({
    @ApiResponse(responseCode = "302", description = "Redireciona para /booking/{patientId}")
  })
  @PostMapping("/{patientId}/appointments/{appointmentId}/cancel")
  public String cancelAppointmentFromDashboard(
      @PathVariable Long patientId,
      @PathVariable Long appointmentId,
      RedirectAttributes redirectAttributes) {
    try {
      bookingService.cancelAppointment(patientId, appointmentId);
      redirectAttributes.addFlashAttribute("successMessage", "Agendamento cancelado com sucesso.");
    } catch (IllegalStateException e) {
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/booking/" + patientId;
  }

  /** Confirmation page shown after successful cancellation. */
  @Operation(
      summary = "Página de cancelamento",
      description =
          "Exibe a confirmação de que o agendamento foi cancelado e o horário foi liberado.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de cancelamento renderizada")
  })
  @GetMapping("/cancelled")
  public String cancelled(Model model) {
    model.addAttribute("pageTitle", "Agendamento Cancelado — PRIORIZASUS");
    return "booking/cancelled";
  }

  private Map<LocalDate, List<Slot>> groupSlotsByDay(List<Slot> slots) {
    Map<LocalDate, List<Slot>> groupedSlots = new LinkedHashMap<>();
    ZoneId clinicZone = clinicTimeZone.getZone();
    for (Slot slot : slots) {
      if (slot.getSlotDateTime() == null) {
        continue;
      }
      LocalDate slotDay = slot.getSlotDateTime().atZone(clinicZone).toLocalDate();
      groupedSlots.computeIfAbsent(slotDay, ignored -> new ArrayList<>()).add(slot);
    }
    return groupedSlots;
  }
}
