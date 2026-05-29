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
import org.springframework.stereotype.Component;

@Configuration
@Profile("test")
class TestPatientSeedConfig {

  @Bean
  CommandLineRunner seedTestPatients(
      PatientRepository patientRepository,
      PatientCategoryStore patientCategoryStore,
      TestPatientSeedStore testPatientSeedStore) {
    return args -> {
      if (testPatientSeedStore.hasPatients()) {
        return;
      }

      LocalDate today = LocalDate.now();
      List<Patient> patients = new ArrayList<>();

      for (int index = 1; index <= 50; index++) {
        patients.add(PatientSeedDataFactory.buildPatient(index, today));
      }

      List<Patient> savedPatients = patientRepository.saveAll(patients);
      patientCategoryStore.clear();
      patientCategoryStore.registerAll(savedPatients);
      testPatientSeedStore.replaceAll(savedPatients);
    };
  }
}

@Component
class TestPatientSeedStore {

  private final List<Patient> patients = new ArrayList<>();

  synchronized void replaceAll(List<Patient> seededPatients) {
    patients.clear();
    patients.addAll(seededPatients);
  }

  synchronized boolean hasPatients() {
    return !patients.isEmpty();
  }

  synchronized List<Patient> getPatients() {
    return List.copyOf(patients);
  }
}
