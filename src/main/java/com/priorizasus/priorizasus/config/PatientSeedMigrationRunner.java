package com.priorizasus.priorizasus.config;

import com.priorizasus.priorizasus.repository.PatientRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Rebuilds the in-memory category store on every startup so the Weekly Selection and patient views
 * always have category data, even when patients were loaded by {@link DemoPatientSeedConfig} or a
 * previous run.
 */
@Configuration
@Profile("default")
class PatientSeedMigrationRunner {

  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  CommandLineRunner rebuildCategoryStore(
      PatientRepository patientRepository, PatientCategoryStore patientCategoryStore) {
    return args -> {
      patientCategoryStore.clear();
      patientCategoryStore.registerAll(patientRepository.findAll());
    };
  }
}
