package com.priorizasus.priorizasus.repository;

import com.priorizasus.priorizasus.entity.Category;
import com.priorizasus.priorizasus.entity.CategoryType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Category} entities.
 *
 * <p>Used by PatientService, CategoryService, and ScoringService for eligibility checks and
 * targetDate calculations.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

  /** Active categories for a given Patient. */
  List<Category> findByPatientIdAndActiveTrue(Long patientId);

  /** All active categories of a given type (e.g., all PRENATAL). */
  List<Category> findByTypeAndActiveTrue(CategoryType type);

  /** All active categories for ACTIVE Patients — used by Weekly Selection. */
  @Query("SELECT c FROM Category c WHERE c.patient.status = 'ACTIVE' AND c.active = true")
  List<Category> findAllEligibleForSelection();
}
