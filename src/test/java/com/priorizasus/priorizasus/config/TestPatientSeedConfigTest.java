package com.priorizasus.priorizasus.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.priorizasus.priorizasus.entity.Category;
import com.priorizasus.priorizasus.entity.CategoryType;
import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.repository.PatientRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TestPatientSeedConfigTest {

  @Autowired private PatientRepository patientRepository;

  @Autowired private TestPatientSeedStore testPatientSeedStore;

  @Test
  void createsFiftyDifferentiatedPatients() {
    List<Patient> patients = testPatientSeedStore.getPatients();

    assertEquals(50, patients.size());
    assertEquals(50L, patientRepository.count());

    Set<String> cpfs = new HashSet<>();
    int singleCategoryCount = 0;
    int multiCategoryCount = 0;
    for (Patient patient : patients) {
      assertTrue(cpfs.add(patient.getCpf()), "CPF must be unique: " + patient.getCpf());
      if (patient.getCategories().size() == 1) {
        singleCategoryCount++;
      } else if (patient.getCategories().size() == 2) {
        multiCategoryCount++;
      }
    }

    assertEquals(30, singleCategoryCount);
    assertEquals(20, multiCategoryCount);
  }

  @Test
  void seededPatientsProduceDistinctPriorityBands() {
    LocalDate today = LocalDate.now();
    List<Patient> ranked =
        testPatientSeedStore.getPatients().stream()
            .sorted(
                Comparator.comparingInt((Patient patient) -> calculateScore(patient, today))
                    .reversed())
            .toList();

    assertTrue(describeCategoryGroup(ranked.get(0), today).contains("Prenatal 36+"));
    assertTrue(describeCategoryGroup(ranked.get(9), today).contains("Prenatal 36+"));
    assertTrue(describeCategoryGroup(ranked.get(10), today).contains("Child + Chronic"));
    assertTrue(describeCategoryGroup(ranked.get(19), today).contains("Child + Chronic"));
    assertTrue(describeCategoryGroup(ranked.get(20), today).contains("Child"));
    assertTrue(describeCategoryGroup(ranked.get(29), today).contains("Child"));
    assertTrue(describeCategoryGroup(ranked.get(30), today).contains("Prenatal 28-36"));
    assertTrue(describeCategoryGroup(ranked.get(39), today).contains("Prenatal 28-36"));
    assertTrue(describeCategoryGroup(ranked.get(40), today).contains("Prenatal <28"));
    assertTrue(describeCategoryGroup(ranked.get(49), today).contains("Prenatal <28"));
  }

  private String describeCategoryGroup(Patient patient, LocalDate today) {
    var categories = patient.getCategories();
    if (categories == null || categories.isEmpty()) {
      return "";
    }
    if (categories.size() == 2) {
      Set<CategoryType> types =
          categories.stream().map(Category::getType).collect(Collectors.toSet());
      if (types.contains(CategoryType.PRENATAL) && types.contains(CategoryType.CHRONIC)) {
        return "Prenatal 36+ + Chronic";
      }
      if (types.contains(CategoryType.CHILD) && types.contains(CategoryType.CHRONIC)) {
        return "Child + Chronic";
      }
      return "Multi " + types;
    }
    Category cat = categories.get(0);
    if (cat.getType() == CategoryType.CHILD) {
      return "Child";
    }
    if (cat.getType() == CategoryType.CHRONIC) {
      return "Chronic";
    }
    // PRENATAL
    int weight = cat.getWeight(today);
    if (weight >= 1000) {
      return "Prenatal 36+";
    }
    if (weight >= 500) {
      return "Prenatal 28-36";
    }
    return "Prenatal <28";
  }

  private int calculateScore(Patient patient, LocalDate today) {
    int score = 0;
    if (patient.getCategories() == null) {
      return score;
    }

    for (var category : patient.getCategories()) {
      score += category.getWeight(today);
      if (patient.getTargetDate() != null) {
        long daysOverdue =
            Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(patient.getTargetDate(), today));
        score += Math.min(daysOverdue * 10, 500);
      }
    }

    return score;
  }
}
