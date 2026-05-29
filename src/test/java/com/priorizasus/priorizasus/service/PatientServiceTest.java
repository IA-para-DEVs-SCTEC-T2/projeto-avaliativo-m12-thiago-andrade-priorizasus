package com.priorizasus.priorizasus.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.priorizasus.priorizasus.config.PatientCategoryStore;
import com.priorizasus.priorizasus.entity.Category;
import com.priorizasus.priorizasus.entity.CategoryType;
import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.repository.PatientRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

  @Mock private PatientRepository patientRepository;
  @Mock private PatientCategoryStore patientCategoryStore;

  private PatientService patientService;

  @BeforeEach
  void setUp() {
    patientService = new PatientService(patientRepository, patientCategoryStore);
  }

  @Test
  @DisplayName("register normalizes CPF and registers categories")
  void registerNormalizesCpf() {
    Patient p = new Patient();
    p.setName("Ana");
    p.setCpf("123.456.789-01");

    when(patientRepository.findByCpf("12345678901")).thenReturn(Optional.empty());
    when(patientRepository.save(any()))
        .thenAnswer(
            inv -> {
              Patient arg = inv.getArgument(0);
              arg.setId(1L);
              return arg;
            });
    when(patientCategoryStore.getCategories(1L)).thenReturn(List.of());

    Patient saved = patientService.register(p);

    assertEquals("12345678901", saved.getCpf());
    verify(patientCategoryStore).register(any());
  }

  @Test
  @DisplayName("search filters by name and cpf digits")
  void searchByNameAndCpf() {
    Patient a = new Patient();
    a.setId(1L);
    a.setName("Maria Silva");
    a.setCpf("11122233344");

    Patient b = new Patient();
    b.setId(2L);
    b.setName("Joao");
    b.setCpf("99988877766");

    when(patientRepository.findAll()).thenReturn(List.of(a, b));
    when(patientCategoryStore.getCategories(1L)).thenReturn(List.of());
    when(patientCategoryStore.getCategories(2L)).thenReturn(List.of());

    var byName = patientService.search("maria");
    assertEquals(1, byName.size());

    var byCpf = patientService.search("11122233344");
    assertEquals(1, byCpf.size());
  }

  @Test
  void updateLastConsultationSetsDate() {
    Patient p = new Patient();
    p.setId(5L);
    when(patientRepository.findById(5L)).thenReturn(Optional.of(p));

    patientService.updateLastConsultation(5L);

    verify(patientRepository).save(argThat(saved -> saved.getLastConsultationDate() != null));
  }

  @Test
  void filterByCategoryReturnsEmptyOnBadInput() {
    assertEquals(List.of(), patientService.filterByCategory(null, ""));
    assertEquals(List.of(), patientService.filterByCategory(List.of(), "PRENATAL"));
  }

  @Test
  void filterByCategoryWorks() {
    Patient p = new Patient();
    p.setId(1L);
    Category c = new Category();
    c.setType(CategoryType.PRENATAL);
    p.setCategories(List.of(c));

    var result = patientService.filterByCategory(List.of(p), "PRENATAL");
    assertEquals(1, result.size());
  }

  @Test
  @DisplayName("hydrateCategories loads categories into patient")
  void hydrateCategoriesLoads() {
    Patient p = new Patient();
    p.setId(1L);
    Category c = new Category();
    c.setType(CategoryType.CHRONIC);
    when(patientCategoryStore.getCategories(1L)).thenReturn(List.of(c));

    var result = patientService.hydrateCategories(p);

    assertEquals(1, result.getCategories().size());
  }

  @Test
  @DisplayName("assignCategory delegates to store")
  void assignCategoryDelegates() {
    Category c = new Category();
    c.setType(CategoryType.PRENATAL);
    when(patientCategoryStore.assignCategory(1L, c)).thenReturn(c);

    var result = patientService.assignCategory(1L, c);

    assertNotNull(result);
    verify(patientCategoryStore).assignCategory(1L, c);
  }

  @Test
  @DisplayName("removeCategory delegates to store")
  void removeCategoryDelegates() {
    when(patientCategoryStore.removeCategory(1L, 5L)).thenReturn(true);

    var result = patientService.removeCategory(1L, 5L);

    assertTrue(result);
  }

  @Test
  @DisplayName("removeCategory returns false for unknown")
  void removeCategoryReturnsFalse() {
    when(patientCategoryStore.removeCategory(99L, 99L)).thenReturn(false);

    var result = patientService.removeCategory(99L, 99L);

    assertFalse(result);
  }

  @Test
  @DisplayName("suspend changes patient status to SUSPENDED")
  void suspendChangesStatus() {
    Patient p = new Patient();
    p.setId(1L);
    p.setStatus(com.priorizasus.priorizasus.entity.PatientStatus.ACTIVE);
    when(patientRepository.findById(1L)).thenReturn(Optional.of(p));

    patientService.suspend(1L, "Violation");

    assertEquals(com.priorizasus.priorizasus.entity.PatientStatus.SUSPENDED, p.getStatus());
    verify(patientRepository).save(p);
  }

  @Test
  @DisplayName("register throws on duplicate CPF")
  void registerDuplicateCpfThrows() {
    Patient existing = new Patient();
    existing.setId(99L);
    existing.setCpf("12345678901");

    Patient p = new Patient();
    p.setName("Ana");
    p.setCpf("123.456.789-01");

    when(patientRepository.findByCpf("12345678901")).thenReturn(Optional.of(existing));

    assertThrows(IllegalArgumentException.class, () -> patientService.register(p));
  }

  @Test
  @DisplayName("update modifies existing patient fields")
  void updateModifiesExisting() {
    Patient existing = new Patient();
    existing.setId(1L);
    existing.setName("Old Name");
    existing.setCpf("11122233344");

    Patient updated = new Patient();
    updated.setId(1L);
    updated.setName("New Name");
    updated.setCpf("111.222.333-44");
    updated.setBirthDate(java.time.LocalDate.of(1990, 1, 1));
    updated.setPhone("123456789");
    updated.setEmail("new@email.com");

    when(patientRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(patientRepository.findByCpf("11122233344")).thenReturn(Optional.of(existing));
    when(patientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(patientCategoryStore.getCategories(1L)).thenReturn(List.of());

    Patient result = patientService.update(updated);

    assertEquals("New Name", result.getName());
    assertEquals("11122233344", result.getCpf());
    verify(patientRepository).save(any());
  }

  @Test
  @DisplayName("delete sets patient status to INACTIVE")
  void deleteSetsInactive() {
    Patient p = new Patient();
    p.setId(1L);
    p.setStatus(com.priorizasus.priorizasus.entity.PatientStatus.ACTIVE);
    when(patientRepository.findById(1L)).thenReturn(Optional.of(p));

    patientService.delete(1L);

    assertEquals(com.priorizasus.priorizasus.entity.PatientStatus.INACTIVE, p.getStatus());
    verify(patientRepository).save(p);
  }

  @Test
  @DisplayName("changeStatus modifies patient status")
  void changeStatusModifies() {
    Patient p = new Patient();
    p.setId(1L);
    p.setStatus(com.priorizasus.priorizasus.entity.PatientStatus.ACTIVE);
    when(patientRepository.findById(1L)).thenReturn(Optional.of(p));

    patientService.changeStatus(1L, com.priorizasus.priorizasus.entity.PatientStatus.SUSPENDED);

    assertEquals(com.priorizasus.priorizasus.entity.PatientStatus.SUSPENDED, p.getStatus());
    verify(patientRepository).save(p);
  }

  @Test
  @DisplayName("findById returns hydrated patient")
  void findByIdHydrated() {
    Patient p = new Patient();
    p.setId(1L);
    p.setName("Test");
    when(patientRepository.findById(1L)).thenReturn(Optional.of(p));
    when(patientCategoryStore.getCategories(1L)).thenReturn(List.of());

    Optional<Patient> result = patientService.findById(1L);
    assertTrue(result.isPresent());
    assertEquals("Test", result.get().getName());
  }

  @Test
  @DisplayName("findById returns empty for unknown")
  void findByIdEmpty() {
    when(patientRepository.findById(99L)).thenReturn(Optional.empty());
    assertTrue(patientService.findById(99L).isEmpty());
  }

  @Test
  @DisplayName("findByCpf returns hydrated patient")
  void findByCpfHydrated() {
    Patient p = new Patient();
    p.setId(1L);
    p.setCpf("12345678901");
    when(patientRepository.findByCpf("12345678901")).thenReturn(Optional.of(p));
    when(patientCategoryStore.getCategories(1L)).thenReturn(List.of());

    Optional<Patient> result = patientService.findByCpf("12345678901");
    assertTrue(result.isPresent());
  }

  @Test
  @DisplayName("findAllActive returns active patients")
  void findAllActiveReturnsActive() {
    Patient p = new Patient();
    p.setId(1L);
    when(patientRepository.findByStatus(com.priorizasus.priorizasus.entity.PatientStatus.ACTIVE))
        .thenReturn(List.of(p));
    when(patientCategoryStore.getCategories(1L)).thenReturn(List.of());

    List<Patient> result = patientService.findAllActive();
    assertEquals(1, result.size());
  }

  @Test
  @DisplayName("findByCategory returns matching patients")
  void findByCategoryReturnsMatching() {
    Patient p = new Patient();
    p.setId(1L);
    p.setStatus(com.priorizasus.priorizasus.entity.PatientStatus.ACTIVE);
    Category c = new Category();
    c.setType(CategoryType.PRENATAL);
    p.setCategories(List.of(c));

    when(patientRepository.findByStatus(com.priorizasus.priorizasus.entity.PatientStatus.ACTIVE))
        .thenReturn(List.of(p));
    when(patientCategoryStore.getCategories(1L)).thenReturn(List.of(c));

    List<Patient> result = patientService.findByCategory("PRENATAL");
    assertEquals(1, result.size());
  }

  @Test
  @DisplayName("findByCategory with blank returns empty")
  void findByCategoryBlankReturnsEmpty() {
    assertTrue(patientService.findByCategory("").isEmpty());
    assertTrue(patientService.findByCategory(null).isEmpty());
  }
}
