package com.priorizasus.priorizasus.service;

import com.priorizasus.priorizasus.annotation.ReqId;
import com.priorizasus.priorizasus.config.PatientCategoryStore;
import com.priorizasus.priorizasus.entity.Category;
import com.priorizasus.priorizasus.entity.CategoryType;
import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.entity.PatientStatus;
import com.priorizasus.priorizasus.repository.PatientRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stub service for Patient operations. Full implementation per Task-PM-05 through PM-09.
 *
 * <p>This stub enables Thymeleaf controller compilation and testing. Replace with full
 * implementation that includes {@code @ReqId} annotations on all public methods.
 */
@Service
@Transactional
public class PatientService {

  private static final Logger log = LoggerFactory.getLogger(PatientService.class);

  private final PatientRepository patientRepository;
  private final PatientCategoryStore patientCategoryStore;

  public PatientService(
      PatientRepository patientRepository, PatientCategoryStore patientCategoryStore) {
    this.patientRepository = patientRepository;
    this.patientCategoryStore = patientCategoryStore;
  }

  @ReqId("PM-001")
  public Patient register(Patient patient) {
    log.debug("Register patient {}", patient.getName());
    String normalizedCpf = normalizeCpf(patient.getCpf());
    if (normalizedCpf == null || normalizedCpf.length() != 11) {
      throw new IllegalArgumentException("CPF deve conter 11 dígitos");
    }

    patientRepository
        .findByCpf(normalizedCpf)
        .ifPresent(
            found -> {
              throw new IllegalArgumentException("CPF já cadastrado.");
            });
    patient.setCpf(normalizedCpf);
    Patient saved = patientRepository.save(patient);
    patientCategoryStore.register(saved);
    return hydrateCategories(saved);
  }

  @ReqId("PM-001")
  @Transactional(readOnly = true)
  public Optional<Patient> findById(Long id) {
    log.debug("Find patient by id {}", id);
    return patientRepository.findById(id).map(this::hydrateCategories);
  }

  @ReqId("PM-001")
  @Transactional(readOnly = true)
  public Optional<Patient> findByCpf(String cpf) {
    log.debug("Find patient by CPF {}", cpf);
    return patientRepository.findByCpf(cpf).map(this::hydrateCategories);
  }

  @ReqId("PM-001")
  @Transactional(readOnly = true)
  public List<Patient> findAllActive() {
    log.debug("Find all ACTIVE patients");
    return patientRepository.findByStatus(PatientStatus.ACTIVE).stream()
        .map(this::hydrateCategories)
        .toList();
  }

  @ReqId("PM-002")
  @Transactional(readOnly = true)
  public List<Patient> findByCategory(String category) {
    log.debug("Find patients by category {}", category);
    if (category == null || category.isBlank()) {
      return new ArrayList<>();
    }

    CategoryType requestedType;
    try {
      requestedType = CategoryType.valueOf(category.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return new ArrayList<>();
    }

    return findAllActive().stream()
        .filter(
            patient ->
                patient.getCategories().stream()
                    .anyMatch(categoryItem -> categoryItem.getType() == requestedType))
        .toList();
  }

  @ReqId("PM-001")
  @Transactional(readOnly = true)
  public List<Patient> search(String query) {
    log.debug("Search patients {}", query);
    if (query == null || query.isBlank()) {
      return new ArrayList<>();
    }

    String normalizedQuery = query.trim().toLowerCase();
    String cpfQuery = query.replaceAll("\\D", "");
    return patientRepository.findAll().stream()
        .map(this::hydrateCategories)
        .filter(
            patient ->
                patient.getStatus() != com.priorizasus.priorizasus.entity.PatientStatus.INACTIVE)
        .filter(
            patient -> {
              String patientName = patient.getName() != null ? patient.getName().toLowerCase() : "";
              String patientCpf =
                  patient.getCpf() != null ? patient.getCpf().replaceAll("\\D", "") : "";
              return patientName.contains(normalizedQuery)
                  || (!cpfQuery.isBlank() && patientCpf.contains(cpfQuery));
            })
        .toList();
  }

  /**
   * Filters a list of patients to only those belonging to the given category.
   *
   * <p>Used to compose AND logic when both category and text search are provided.
   *
   * @param patients the list to filter
   * @param category the {@link CategoryType} name (e.g. PRENATAL, CHILD, CHRONIC)
   * @return filtered list (possibly empty); never null
   */
  public List<Patient> filterByCategory(List<Patient> patients, String category) {
    if (category == null || category.isBlank() || patients == null || patients.isEmpty()) {
      return patients != null ? patients : new ArrayList<>();
    }
    CategoryType requestedType;
    try {
      requestedType = CategoryType.valueOf(category.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return new ArrayList<>();
    }
    return patients.stream()
        .filter(p -> p.getCategories().stream().anyMatch(c -> c.getType() == requestedType))
        .toList();
  }

  @ReqId("PM-005")
  @Transactional
  public void updateLastConsultation(Long patientId) {
    log.debug("Update last consultation for patient {}", patientId);
    Patient patient =
        patientRepository
            .findById(patientId)
            .orElseThrow(() -> new EntityNotFoundException("Patient not found"));
    patient.setLastConsultationDate(java.time.LocalDate.now());
    patientRepository.save(patient);
  }

  @ReqId("PM-004")
  @Transactional
  public Patient update(Patient updatedPatient) {
    log.debug("Update patient {}", updatedPatient.getId());
    Patient existing =
        patientRepository
            .findById(updatedPatient.getId())
            .orElseThrow(() -> new EntityNotFoundException("Patient not found"));

    String normalizedCpf = normalizeCpf(updatedPatient.getCpf());
    patientRepository
        .findByCpf(normalizedCpf)
        .filter(found -> !found.getId().equals(existing.getId()))
        .ifPresent(
            found -> {
              throw new IllegalArgumentException("CPF já cadastrado para outro paciente.");
            });

    existing.setName(updatedPatient.getName());
    existing.setCpf(normalizedCpf);
    existing.setBirthDate(updatedPatient.getBirthDate());
    existing.setPhone(updatedPatient.getPhone());
    existing.setEmail(updatedPatient.getEmail());
    existing.setRegistrationDate(updatedPatient.getRegistrationDate());
    existing.setAddress(updatedPatient.getAddress());
    existing.setLastConsultationDate(updatedPatient.getLastConsultationDate());
    existing.setTargetDate(updatedPatient.getTargetDate());

    Patient saved = patientRepository.save(existing);
    return hydrateCategories(saved);
  }

  @ReqId("PM-004")
  @Transactional
  public void delete(Long patientId) {
    log.debug("Delete patient {}", patientId);
    Patient patient =
        patientRepository
            .findById(patientId)
            .orElseThrow(() -> new EntityNotFoundException("Patient not found"));
    patient.setStatus(PatientStatus.INACTIVE);
    patientRepository.save(patient);
  }

  @ReqId("PM-004")
  @Transactional
  public void suspend(Long patientId, String reason) {
    log.debug("Suspend patient {} reason: {}", patientId, reason);
    Patient patient =
        patientRepository
            .findById(patientId)
            .orElseThrow(() -> new EntityNotFoundException("Patient not found"));
    patient.setStatus(PatientStatus.SUSPENDED);
    patientRepository.save(patient);
  }

  @ReqId("PM-004")
  @Transactional
  public void changeStatus(Long patientId, PatientStatus status) {
    log.debug("Change status for patient {} to {}", patientId, status);
    Patient patient =
        patientRepository
            .findById(patientId)
            .orElseThrow(() -> new EntityNotFoundException("Patient not found"));
    patient.setStatus(status);
    patientRepository.save(patient);
  }

  @ReqId("PM-001")
  public Patient hydrateCategories(Patient patient) {
    List<Category> categories = patientCategoryStore.getCategories(patient.getId());
    patient.setCategories(new ArrayList<>(categories));
    return patient;
  }

  /** Assigns a category to a patient. */
  @ReqId("PM-002")
  @Transactional
  public Category assignCategory(Long patientId, Category category) {
    log.debug("Assign category {} to patient {}", category.getType(), patientId);
    Category assigned = patientCategoryStore.assignCategory(patientId, category);
    return assigned;
  }

  /** Removes a category from a patient. Returns true if removed. */
  @ReqId("PM-002")
  @Transactional
  public boolean removeCategory(Long patientId, Long categoryId) {
    log.debug("Remove category {} from patient {}", categoryId, patientId);
    return patientCategoryStore.removeCategory(patientId, categoryId);
  }

  private String normalizeCpf(String cpf) {
    return cpf == null ? null : cpf.replaceAll("\\D", "");
  }
}
