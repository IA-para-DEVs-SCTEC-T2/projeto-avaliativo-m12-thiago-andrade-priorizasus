package com.priorizasus.priorizasus.controller;

import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller for Patient CRUD operations.
 *
 * <p>Guardrail G2: No {@code @Transactional}. Guardrail G3: No direct repository access. Guardrail
 * G11: Zero business logic — delegates to {@link PatientService}.
 */
@RestController
@RequestMapping("/api/patients")
@Tag(name = "Patients", description = "Cadastro e consulta de pacientes")
public class PatientApiController {

  private static final Logger log = LoggerFactory.getLogger(PatientApiController.class);

  private final PatientService patientService;

  public PatientApiController(PatientService patientService) {
    this.patientService = patientService;
  }

  @Operation(
      summary = "Listar pacientes",
      description =
          "Lista todos os pacientes ativos, com filtros opcionais por categoria e busca textual.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Lista de pacientes retornada com sucesso")
  })
  @GetMapping
  public ResponseEntity<List<Patient>> listPatients(
      @Parameter(description = "Filtrar por categoria (PRENATAL, CHILD, CHRONIC)")
          @RequestParam(required = false)
          String category,
      @Parameter(description = "Busca por nome ou CPF") @RequestParam(required = false)
          String search) {

    List<Patient> patients;
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
    return ResponseEntity.ok(patients);
  }

  @Operation(
      summary = "Buscar paciente por ID",
      description = "Retorna os detalhes de um paciente específico.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Paciente encontrado"),
    @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
  })
  @GetMapping("/{id}")
  public ResponseEntity<Patient> getPatient(
      @Parameter(description = "ID do paciente", required = true) @PathVariable Long id) {
    return patientService
        .findById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @Operation(summary = "Cadastrar paciente", description = "Cadastra um novo paciente no sistema.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Paciente cadastrado com sucesso"),
    @ApiResponse(responseCode = "400", description = "Dados inválidos ou CPF já cadastrado")
  })
  @PostMapping
  public ResponseEntity<?> registerPatient(
      @RequestBody @Schema(implementation = Patient.class) Patient patient) {
    try {
      Patient registered = patientService.register(patient);
      log.info("Patient registered via API: {} (id={})", registered.getName(), registered.getId());
      return ResponseEntity.status(HttpStatus.CREATED).body(registered);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @Operation(
      summary = "Atualizar paciente",
      description = "Atualiza os dados de um paciente existente.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Paciente atualizado com sucesso"),
    @ApiResponse(responseCode = "400", description = "Dados inválidos"),
    @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
  })
  @PutMapping("/{id}")
  public ResponseEntity<?> updatePatient(
      @Parameter(description = "ID do paciente", required = true) @PathVariable Long id,
      @RequestBody Patient patient) {
    try {
      patient.setId(id);
      Patient updated = patientService.update(patient);
      return ResponseEntity.ok(updated);
    } catch (EntityNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @Operation(
      summary = "Remover paciente",
      description = "Marca o paciente como INACTIVE (soft-delete).")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Paciente removido com sucesso"),
    @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletePatient(
      @Parameter(description = "ID do paciente", required = true) @PathVariable Long id) {
    try {
      patientService.delete(id);
      return ResponseEntity.noContent().build();
    } catch (EntityNotFoundException e) {
      return ResponseEntity.notFound().build();
    }
  }
}
