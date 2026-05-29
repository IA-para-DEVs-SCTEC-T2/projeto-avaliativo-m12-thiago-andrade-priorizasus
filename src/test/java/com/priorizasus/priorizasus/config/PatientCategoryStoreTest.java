package com.priorizasus.priorizasus.config;

import static org.junit.jupiter.api.Assertions.*;

import com.priorizasus.priorizasus.entity.Category;
import com.priorizasus.priorizasus.entity.CategoryType;
import com.priorizasus.priorizasus.entity.Patient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PatientCategoryStoreTest {

  private PatientCategoryStore store;

  @BeforeEach
  void setUp() {
    store = new PatientCategoryStore();
  }

  @Test
  @DisplayName("register with null patient id does nothing")
  void registerWithNullId() {
    Patient p = new Patient();
    store.register(p);
    assertTrue(store.getCategories(1L).isEmpty());
  }

  @Test
  @DisplayName("registerAll processes all patients")
  void registerAllProcessesPatients() {
    Patient p1 = new Patient();
    p1.setId(1L);
    p1.setName("Ana");
    p1.setCpf("12345678901");
    Category c = new Category();
    c.setType(CategoryType.CHRONIC);
    p1.setCategories(List.of(c));

    Patient p2 = new Patient();
    p2.setId(2L);
    p2.setName("Joao");
    p2.setCpf("22233344455");
    p2.setCategories(List.of());

    store.registerAll(List.of(p1, p2));

    assertEquals(1, store.getCategories(1L).size());
    assertTrue(store.getCategories(2L).isEmpty());
  }

  @Test
  @DisplayName("getCategories returns empty for unknown patient")
  void getCategoriesUnknown() {
    assertTrue(store.getCategories(999L).isEmpty());
  }

  @Test
  @DisplayName("assignCategory adds category to patient")
  void assignCategoryAdds() {
    Category c = new Category();
    c.setType(CategoryType.PRENATAL);

    Category assigned = store.assignCategory(1L, c);

    assertNotNull(assigned.getId());
    assertTrue(assigned.isActive());
    assertEquals(1, store.getCategories(1L).size());
  }

  @Test
  @DisplayName("removeCategory removes existing category")
  void removeCategoryRemoves() {
    Category c = new Category();
    c.setType(CategoryType.CHRONIC);
    Category assigned = store.assignCategory(1L, c);

    boolean removed = store.removeCategory(1L, assigned.getId());

    assertTrue(removed);
    assertTrue(store.getCategories(1L).isEmpty());
  }

  @Test
  @DisplayName("removeCategory returns false for unknown patient")
  void removeCategoryUnknownPatient() {
    assertFalse(store.removeCategory(999L, 1L));
  }

  @Test
  @DisplayName("removeCategory returns false for unknown category id")
  void removeCategoryUnknownCategory() {
    Category c = new Category();
    c.setType(CategoryType.CHRONIC);
    store.assignCategory(1L, c);

    assertFalse(store.removeCategory(1L, 999L));
  }

  @Test
  @DisplayName("clear removes all categories")
  void clearRemovesAll() {
    Category c = new Category();
    c.setType(CategoryType.CHRONIC);
    store.assignCategory(1L, c);
    store.assignCategory(2L, c);

    store.clear();

    assertTrue(store.getCategories(1L).isEmpty());
    assertTrue(store.getCategories(2L).isEmpty());
  }
}
