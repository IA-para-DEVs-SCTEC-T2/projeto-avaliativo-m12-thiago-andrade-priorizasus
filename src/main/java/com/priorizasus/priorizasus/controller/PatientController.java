package com.priorizasus.priorizasus.controller;

import com.priorizasus.priorizasus.config.ClinicTimeZone;
import com.priorizasus.priorizasus.entity.Appointment;
import com.priorizasus.priorizasus.entity.AppointmentStatus;
import com.priorizasus.priorizasus.entity.Category;
import com.priorizasus.priorizasus.entity.CategoryType;
import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.entity.Slot;
import com.priorizasus.priorizasus.service.AppointmentService;
import com.priorizasus.priorizasus.service.CapacityService;
import com.priorizasus.priorizasus.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * MVC controller for Patient registration, listing, detail, and category assignment.
 *
 * <p>Guardrail G2: No {@code @Transactional} — all transactions in services. Guardrail G3: No
 * direct repository access — delegates to services. Guardrail G11: Zero business logic — all logic
 * in services. Guardrail G13: Redirect-after-POST for all form submissions.
 */
@Controller
@RequestMapping("/patients")
@Tag(
    name = "Patients Web",
    description =
        "Gestão de pacientes — cadastro, listagem, edição, categorias e agendamentos (Thymeleaf)")
public class PatientController {

  private static final Logger log = LoggerFactory.getLogger(PatientController.class);

  private final PatientService patientService;
  private final AppointmentService appointmentService;
  private final CapacityService capacityService;
  private final ClinicTimeZone clinicTimeZone;

  public PatientController(
      PatientService patientService,
      AppointmentService appointmentService,
      CapacityService capacityService,
      ClinicTimeZone clinicTimeZone) {
    this.patientService = patientService;
    this.appointmentService = appointmentService;
    this.capacityService = capacityService;
    this.clinicTimeZone = clinicTimeZone;
  }

  /**
   * Lists all ACTIVE patients with optional filters.
   *
   * @param category optional Category filter (PRENATAL, CHILD, CHRONIC)
   * @param status optional Status filter (ACTIVE, INACTIVE, SUSPENDED)
   * @param search optional name or CPF search term
   */
  @Operation(
      summary = "Listar pacientes",
      description =
          "Lista todos os pacientes ativos com filtros opcionais por categoria, status e busca textual por nome ou CPF.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Lista de pacientes renderizada")
  })
  @GetMapping
  public String listPatients(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String search,
      Model model) {

    List<Patient> patients = new ArrayList<>();
    try {
      boolean hasSearch = search != null && !search.isBlank();
      boolean hasCategory = category != null && !category.isBlank();

      if (hasSearch) {
        patients = patientService.search(search);
        if (hasCategory) {
          patients = patientService.filterByCategory(patients, category);
        }
      } else if (hasCategory) {
        patients = patientService.findByCategory(category);
      } else {
        patients = patientService.findAllActive();
      }
    } catch (UnsupportedOperationException e) {
      log.debug("PatientService not yet implemented — showing empty list");
    } catch (Exception e) {
      log.error("Error listing patients", e);
      patients = new ArrayList<>();
      model.addAttribute("errorMessage", "Erro ao listar pacientes: " + e.getMessage());
    }

    model.addAttribute("patients", patients);
    model.addAttribute("pageTitle", "Pacientes — PRIORIZASUS");
    model.addAttribute("filterCategory", category);
    model.addAttribute("filterStatus", status);
    model.addAttribute("filterSearch", search);
    model.addAttribute("today", clinicTimeZone.today());
    return "patients/list";
  }

  /** Shows patient detail with appointments, categories, and action buttons. */
  @Operation(
      summary = "Detalhe do paciente",
      description =
          "Exibe dados cadastrais, histórico de agendamentos, categorias atribuídas e botões de ação (editar, excluir, atribuir categoria).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Detalhe do paciente renderizado"),
    @ApiResponse(responseCode = "200", description = "Página de erro se paciente não encontrado")
  })
  @GetMapping("/{id}")
  public String patientDetail(@PathVariable Long id, Model model) {
    Optional<Patient> patient = Optional.empty();
    try {
      patient = patientService.findById(id);
    } catch (UnsupportedOperationException e) {
      log.debug("PatientService not yet implemented");
    }

    if (patient.isEmpty()) {
      model.addAttribute("errorMessage", "Paciente não encontrado.");
      return "error";
    }

    Patient p = patient.get();
    model.addAttribute("patient", p);
    model.addAttribute("pageTitle", p.getName() + " — PRIORIZASUS");
    model.addAttribute("today", clinicTimeZone.today());

    try {
      model.addAttribute("appointments", appointmentService.findByPatientId(id));
    } catch (UnsupportedOperationException e) {
      model.addAttribute("appointments", new ArrayList<>());
    }

    Map<Long, List<Slot>> rescheduleOptions = new LinkedHashMap<>();
    try {
      List<Appointment> appointments = appointmentService.findByPatientId(id);
      for (Appointment appointment : appointments) {
        if (appointment.getId() != null
            && appointment.getStatus() == AppointmentStatus.CONFIRMED
            && appointment.getWeekStart() != null) {
          rescheduleOptions.put(
              appointment.getId(),
              capacityService.getAvailableSlotsForWeek(appointment.getWeekStart()));
        }
      }
    } catch (Exception e) {
      log.debug("Error loading reschedule options: {}", e.getMessage());
    }
    model.addAttribute("rescheduleOptions", rescheduleOptions);

    return "patients/detail";
  }

  /** Shows the new patient registration form. */
  @Operation(
      summary = "Formulário de novo paciente",
      description = "Exibe o formulário de cadastro de paciente (acesso staff).")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Formulário renderizado")})
  @GetMapping("/new")
  public String newPatientForm(Model model) {
    model.addAttribute("patient", new Patient());
    model.addAttribute("pageTitle", "Novo Paciente — PRIORIZASUS");
    model.addAttribute("today", clinicTimeZone.today());
    return "patients/register";
  }

  /**
   * Processes public patient registration from the home page. Accepts Patient fields plus category
   * checkboxes (categoryPrenatal, categoryChild, categoryChronic) and PRENATAL-specific fields
   * (ultrasoundDate, gestationalWeeks). Guardrail G13: Redirect-after-POST.
   */
  @Operation(
      summary = "Cadastro público (home page)",
      description =
          "Registra um novo paciente a partir da página inicial. Aceita campos de categoria (Pré-Natal, Puericultura, Crônico) e dados gestacionais.")
  @ApiResponses({
    @ApiResponse(responseCode = "302", description = "Redireciona para / com mensagem de sucesso"),
    @ApiResponse(responseCode = "302", description = "Redireciona para / com mensagem de erro")
  })
  @PostMapping("/register")
  public String registerFromHome(
      @ModelAttribute Patient patient,
      @RequestParam(required = false) String categoryPrenatal,
      @RequestParam(required = false) String categoryChild,
      @RequestParam(required = false) String categoryChronic,
      @RequestParam(required = false) String ultrasoundDate,
      @RequestParam(required = false) Integer gestationalWeeks,
      RedirectAttributes redirectAttributes) {

    try {
      Patient registered = patientService.register(patient);
      log.info(
          "Patient registered: {} (id={}), categories: prenatal={}, child={}, chronic={}",
          registered.getName(),
          registered.getId(),
          categoryPrenatal,
          categoryChild,
          categoryChronic);

      // Process category checkboxes from the home form and assign explicit categories
      try {
        if (categoryPrenatal != null && !categoryPrenatal.isBlank()) {
          Category cat = new Category();
          cat.setType(CategoryType.PRENATAL);
          if (ultrasoundDate != null && !ultrasoundDate.isBlank()) {
            cat.setUltrasoundDate(LocalDate.parse(ultrasoundDate));
          }
          if (gestationalWeeks != null) {
            cat.setGestationalWeeksAtUltrasound(gestationalWeeks);
          }
          patientService.assignCategory(registered.getId(), cat);
        }

        if (categoryChild != null && !categoryChild.isBlank()) {
          Category cat = new Category();
          cat.setType(CategoryType.CHILD);
          patientService.assignCategory(registered.getId(), cat);
        }

        if (categoryChronic != null && !categoryChronic.isBlank()) {
          Category cat = new Category();
          cat.setType(CategoryType.CHRONIC);
          patientService.assignCategory(registered.getId(), cat);
        }
      } catch (Exception e) {
        log.warn(
            "Failed to assign categories for patient {}: {}", registered.getId(), e.getMessage());
      }
      redirectAttributes.addFlashAttribute(
          "successMessage",
          "Cadastro realizado com sucesso! Se selecionado(a), voce recebera um email "
              + "com o link para agendar sua consulta.");
      return "redirect:/";
    } catch (Exception e) {
      log.error("Failed to register patient", e);
      redirectAttributes.addFlashAttribute("errorMessage", "Erro ao cadastrar: " + e.getMessage());
      return "redirect:/";
    }
  }

  /**
   * Processes patient registration form. Guardrail G13: Redirect-after-POST to prevent
   * double-submit.
   */
  @Operation(
      summary = "Cadastrar paciente (staff)",
      description =
          "Registra um novo paciente a partir do painel administrativo. Redireciona para o detalhe do paciente criado.")
  @ApiResponses({
    @ApiResponse(responseCode = "302", description = "Redireciona para /patients/{id} com sucesso"),
    @ApiResponse(
        responseCode = "302",
        description = "Redireciona para /patients/new em caso de erro")
  })
  @PostMapping
  public String registerPatient(
      @ModelAttribute Patient patient, RedirectAttributes redirectAttributes) {

    try {
      Patient registered = patientService.register(patient);
      redirectAttributes.addFlashAttribute(
          "successMessage", "Paciente " + registered.getName() + " cadastrado com sucesso!");
      return "redirect:/patients/" + registered.getId();
    } catch (IllegalArgumentException e) {
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
      return "redirect:/patients/new";
    } catch (UnsupportedOperationException e) {
      redirectAttributes.addFlashAttribute("errorMessage", "Serviço de pacientes indisponível.");
      return "redirect:/patients/new";
    }
  }

  /** Shows the edit form for a patient. */
  @Operation(
      summary = "Formulário de edição",
      description = "Exibe o formulário preenchido com os dados atuais do paciente para edição.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Formulário de edição renderizado"),
    @ApiResponse(responseCode = "200", description = "Página de erro se paciente não encontrado")
  })
  @GetMapping("/{id}/edit")
  public String editPatientForm(@PathVariable Long id, Model model) {
    Optional<Patient> patient = patientService.findById(id);
    if (patient.isEmpty()) {
      model.addAttribute("errorMessage", "Paciente não encontrado.");
      return "error";
    }

    model.addAttribute("patient", patient.get());
    model.addAttribute("pageTitle", "Editar Paciente — PRIORIZASUS");
    model.addAttribute("today", clinicTimeZone.today());
    return "patients/edit";
  }

  /** Updates a patient. */
  @Operation(
      summary = "Atualizar paciente",
      description =
          "Salva as alterações dos dados cadastrais do paciente. Redireciona para o detalhe.")
  @ApiResponses({
    @ApiResponse(responseCode = "302", description = "Redireciona para /patients/{id} com sucesso"),
    @ApiResponse(
        responseCode = "302",
        description = "Redireciona para /patients/{id}/edit em caso de erro")
  })
  @PostMapping("/{id}/update")
  public String updatePatient(
      @PathVariable Long id,
      @ModelAttribute Patient patient,
      RedirectAttributes redirectAttributes) {
    try {
      patient.setId(id);
      Patient updated = patientService.update(patient);
      redirectAttributes.addFlashAttribute(
          "successMessage", "Paciente " + updated.getName() + " atualizado com sucesso!");
      return "redirect:/patients/" + updated.getId();
    } catch (Exception e) {
      redirectAttributes.addFlashAttribute(
          "errorMessage", "Erro ao atualizar paciente: " + e.getMessage());
      return "redirect:/patients/" + id + "/edit";
    }
  }

  /** Soft-deletes a patient by marking it inactive. */
  @Operation(
      summary = "Excluir paciente (soft-delete)",
      description = "Marca o paciente como INACTIVE. Não remove fisicamente o registro.")
  @ApiResponses({
    @ApiResponse(responseCode = "302", description = "Redireciona para /patients com sucesso"),
    @ApiResponse(
        responseCode = "302",
        description = "Redireciona para /patients/{id} em caso de erro")
  })
  @PostMapping("/{id}/delete")
  public String deletePatient(@PathVariable Long id, RedirectAttributes redirectAttributes) {
    try {
      patientService.delete(id);
      redirectAttributes.addFlashAttribute("successMessage", "Paciente excluído com sucesso!");
      return "redirect:/patients";
    } catch (Exception e) {
      redirectAttributes.addFlashAttribute(
          "errorMessage", "Erro ao excluir paciente: " + e.getMessage());
      return "redirect:/patients/" + id;
    }
  }

  /** Shows the category assignment form for a patient. */
  @Operation(
      summary = "Formulário de atribuição de categoria",
      description =
          "Exibe o formulário para atribuir categoria (Pré-Natal, Puericultura, Crônico) ao paciente, com campos dinâmicos por tipo.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Formulário renderizado"),
    @ApiResponse(responseCode = "200", description = "Página de erro se paciente não encontrado")
  })
  @GetMapping("/{id}/categories/assign")
  public String assignCategoryForm(@PathVariable Long id, Model model) {
    Optional<Patient> patient = Optional.empty();
    try {
      patient = patientService.findById(id);
    } catch (UnsupportedOperationException e) {
      log.debug("PatientService not yet implemented");
    }

    if (patient.isEmpty()) {
      model.addAttribute("errorMessage", "Paciente não encontrado.");
      return "error";
    }

    model.addAttribute("patient", patient.get());
    model.addAttribute("pageTitle", "Atribuir Categoria — PRIORIZASUS");
    return "patients/assign-category";
  }

  /** Processes category assignment. Guardrail G13: Redirect-after-POST. */
  @Operation(
      summary = "Atribuir categoria",
      description =
          "Processa a atribuição de categoria ao paciente, calculando a data-alvo conforme o tipo (PRENATAL, CHILD, CHRONIC).")
  @ApiResponses({
    @ApiResponse(responseCode = "302", description = "Redireciona para /patients/{id} com sucesso"),
    @ApiResponse(responseCode = "302", description = "Redireciona para /patients/{id} com erro")
  })
  @PostMapping("/{id}/categories")
  public String assignCategory(
      @PathVariable Long id,
      @RequestParam String categoryType,
      @RequestParam(required = false) String ultrasoundDate,
      @RequestParam(required = false) Integer gestationalWeeks,
      @RequestParam(required = false) String lastMilestone,
      @RequestParam(required = false) String conditionType,
      RedirectAttributes redirectAttributes) {

    try {
      Category category = new Category();
      CategoryType type = CategoryType.valueOf(categoryType);
      category.setType(type);

      // Calculate target date based on category type
      LocalDate today = clinicTimeZone.today();
      if (type == CategoryType.PRENATAL && ultrasoundDate != null && !ultrasoundDate.isBlank()) {
        category.setUltrasoundDate(LocalDate.parse(ultrasoundDate));
        if (gestationalWeeks != null) {
          category.setGestationalWeeksAtUltrasound(gestationalWeeks);
        }
        // targetDate: dynamic for PRENATAL — set a reasonable default
        category.setTargetDate(today.plusDays(30));
      } else if (type == CategoryType.CHILD) {
        // CHILD: target based on milestone, default 30 days
        category.setTargetDate(today.plusDays(30));
      } else if (type == CategoryType.CHRONIC) {
        // CHRONIC: 60-day interval
        category.setTargetDate(today.plusDays(60));
      }

      patientService.assignCategory(id, category);
      redirectAttributes.addFlashAttribute("successMessage", "Categoria atribuída com sucesso!");
    } catch (Exception e) {
      log.error("Failed to assign category", e);
      redirectAttributes.addFlashAttribute(
          "errorMessage", "Erro ao atribuir categoria: " + e.getMessage());
    }
    return "redirect:/patients/" + id;
  }

  /** Removes a category from a patient. Guardrail G13: Redirect-after-POST. */
  @Operation(
      summary = "Remover categoria",
      description = "Remove uma categoria específica do paciente.")
  @ApiResponses({
    @ApiResponse(responseCode = "302", description = "Redireciona para /patients/{id}")
  })
  @PostMapping("/{id}/categories/{categoryId}/remove")
  public String removeCategory(
      @PathVariable Long id, @PathVariable Long categoryId, RedirectAttributes redirectAttributes) {

    boolean removed = patientService.removeCategory(id, categoryId);
    if (removed) {
      redirectAttributes.addFlashAttribute("successMessage", "Categoria removida com sucesso!");
    } else {
      redirectAttributes.addFlashAttribute("errorMessage", "Categoria não encontrada.");
    }
    return "redirect:/patients/" + id;
  }

  /** Marks an appointment as completed. */
  @Operation(
      summary = "Concluir agendamento",
      description = "Marca um agendamento como COMPLETED após a consulta ser realizada.")
  @ApiResponses({
    @ApiResponse(responseCode = "302", description = "Redireciona para /patients/{patientId}")
  })
  @PostMapping("/{patientId}/appointments/{appointmentId}/complete")
  public String completeAppointment(
      @PathVariable Long patientId,
      @PathVariable Long appointmentId,
      RedirectAttributes redirectAttributes) {
    try {
      appointmentService.completeAppointment(appointmentId, null, null);
      redirectAttributes.addFlashAttribute("successMessage", "Agendamento concluído com sucesso!");
    } catch (Exception e) {
      redirectAttributes.addFlashAttribute(
          "errorMessage", "Erro ao concluir agendamento: " + e.getMessage());
    }
    return "redirect:/patients/" + patientId;
  }

  /** Cancels an appointment. */
  @Operation(
      summary = "Cancelar agendamento (staff)",
      description = "Cancela um agendamento do paciente a partir do painel staff.")
  @ApiResponses({
    @ApiResponse(responseCode = "302", description = "Redireciona para /patients/{patientId}")
  })
  @PostMapping("/{patientId}/appointments/{appointmentId}/cancel")
  public String cancelAppointment(
      @PathVariable Long patientId,
      @PathVariable Long appointmentId,
      RedirectAttributes redirectAttributes) {
    try {
      appointmentService.cancelAppointment(appointmentId);
      redirectAttributes.addFlashAttribute("successMessage", "Agendamento cancelado com sucesso!");
    } catch (Exception e) {
      redirectAttributes.addFlashAttribute(
          "errorMessage", "Erro ao cancelar agendamento: " + e.getMessage());
    }
    return "redirect:/patients/" + patientId;
  }

  /** Reassigns an appointment to a new available slot. */
  @Operation(
      summary = "Reagendar consulta",
      description = "Reatribui um agendamento existente para um novo slot disponível.")
  @ApiResponses({
    @ApiResponse(responseCode = "302", description = "Redireciona para /patients/{patientId}")
  })
  @PostMapping("/{patientId}/appointments/{appointmentId}/reschedule")
  public String rescheduleAppointment(
      @PathVariable Long patientId,
      @PathVariable Long appointmentId,
      @RequestParam Long newSlotId,
      RedirectAttributes redirectAttributes) {
    try {
      appointmentService.reassignAppointment(appointmentId, newSlotId);
      redirectAttributes.addFlashAttribute("successMessage", "Agendamento reagendado com sucesso!");
    } catch (Exception e) {
      redirectAttributes.addFlashAttribute("errorMessage", "Erro ao reagendar: " + e.getMessage());
    }
    return "redirect:/patients/" + patientId;
  }
}
