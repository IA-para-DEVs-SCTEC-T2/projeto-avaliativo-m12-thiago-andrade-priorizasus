package com.priorizasus.priorizasus.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.repository.PatientRepository;
import com.priorizasus.priorizasus.service.PatientService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DefaultPatientSeedMigrationTest {

  @Autowired private PatientRepository patientRepository;

  @Autowired private PatientService patientService;

  @Test
  void loadsFiftyPatientsForWeeklySelection() {
    assertEquals(50L, patientRepository.count());

    List<Patient> activePatients = patientService.findAllActive();
    assertEquals(50, activePatients.size());
    assertFalse(patientService.findByCategory("prenatal").isEmpty());
    assertFalse(patientService.findByCategory("child").isEmpty());
    assertFalse(patientService.findByCategory("chronic").isEmpty());
  }
}
