package com.priorizasus.priorizasus.config;

import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.repository.PatientRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("default")
class DemoPatientSeedConfig {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(DemoPatientSeedConfig.class);

  @Bean
  CommandLineRunner seedDemoPatients(
      PatientRepository patientRepository, PatientCategoryStore patientCategoryStore) {
    return args -> {
      if (patientRepository.count() == 0) {
        log.info("Inserting 50 seed patients via JPA...");

        LocalDate today = LocalDate.now();
        List<Patient> patients = new ArrayList<>();

        for (int index = 1; index <= 50; index++) {
          patients.add(PatientSeedDataFactory.buildPatient(index, today));
        }

        List<Patient> savedPatients = patientRepository.saveAll(patients);
        log.info("Inserted {} seed patients.", savedPatients.size());
      } else {
        log.info("Found {} existing patients — skipping insert.", patientRepository.count());
      }

      patientCategoryStore.clear();
      patientCategoryStore.registerAll(patientRepository.findAll());
      log.info("Category store populated with {} entries.", patientRepository.count());
    };
  }
}
