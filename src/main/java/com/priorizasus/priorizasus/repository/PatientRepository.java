package com.priorizasus.priorizasus.repository;

import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.entity.PatientStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for {@link Patient} entities. */
@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

  Optional<Patient> findByCpf(String cpf);

  List<Patient> findByStatus(PatientStatus status);

  List<Patient> findByNameContainingIgnoreCase(String name);
}
