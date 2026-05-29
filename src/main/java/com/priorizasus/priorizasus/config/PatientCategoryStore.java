package com.priorizasus.priorizasus.config;

import com.priorizasus.priorizasus.entity.Category;
import com.priorizasus.priorizasus.entity.Patient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class PatientCategoryStore {

  private final Map<Long, List<Category>> categoriesByPatientId = new ConcurrentHashMap<>();
  private final AtomicLong categoryIdGenerator = new AtomicLong(1);

  public void register(Patient patient) {
    if (patient.getId() == null) {
      return;
    }

    List<Category> categories = patient.getCategories();
    if (categories == null || categories.isEmpty()) {
      categories = deriveSeedCategories(patient);
    }

    if (categories == null || categories.isEmpty()) {
      return;
    }

    // Assign IDs to seed categories
    for (Category cat : categories) {
      if (cat.getId() == null) {
        cat.setId(categoryIdGenerator.getAndIncrement());
      }
    }

    categoriesByPatientId.put(patient.getId(), new ArrayList<>(categories));
  }

  public void registerAll(List<Patient> patients) {
    for (Patient patient : patients) {
      register(patient);
    }
  }

  public List<Category> getCategories(Long patientId) {
    return categoriesByPatientId.getOrDefault(patientId, List.of());
  }

  /** Assigns a new category to a patient. */
  public Category assignCategory(Long patientId, Category category) {
    category.setId(categoryIdGenerator.getAndIncrement());
    category.setActive(true);
    List<Category> existing =
        new ArrayList<>(categoriesByPatientId.getOrDefault(patientId, List.of()));
    existing.add(category);
    categoriesByPatientId.put(patientId, existing);
    return category;
  }

  /** Removes a category from a patient by category ID. Returns true if removed. */
  public boolean removeCategory(Long patientId, Long categoryId) {
    List<Category> existing = categoriesByPatientId.get(patientId);
    if (existing == null) {
      return false;
    }
    List<Category> updated = new ArrayList<>(existing);
    boolean removed = updated.removeIf(cat -> cat.getId().equals(categoryId));
    if (removed) {
      categoriesByPatientId.put(patientId, updated);
    }
    return removed;
  }

  public void clear() {
    categoriesByPatientId.clear();
  }

  private List<Category> deriveSeedCategories(Patient patient) {
    String cpf = patient.getCpf();
    if (cpf == null || cpf.isBlank()) {
      return List.of();
    }

    String digits = cpf.replaceAll("\\D", "");
    if (digits.isBlank()) {
      return List.of();
    }

    try {
      long index = Long.parseLong(digits);
      if (index <= 0 || index > Integer.MAX_VALUE) {
        return List.of();
      }
      return PatientSeedDataFactory.buildCategories((int) index, LocalDate.now());
    } catch (NumberFormatException ex) {
      return List.of();
    }
  }
}
