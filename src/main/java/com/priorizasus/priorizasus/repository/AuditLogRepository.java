package com.priorizasus.priorizasus.repository;

import com.priorizasus.priorizasus.entity.AuditActionType;
import com.priorizasus.priorizasus.entity.AuditLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link AuditLog} — immutable audit trail entries.
 *
 * <p>Provides query methods for filtering by action type, Patient, and date range. No pessimistic
 * locking needed here — audit logs are append-only.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  /** Finds all audit entries of a given action type. */
  List<AuditLog> findByActionType(AuditActionType actionType);

  /** Finds all audit entries related to a specific Patient. */
  List<AuditLog> findByPatientId(Long patientId);

  /** Finds all audit entries within a date range (UTC timestamps). */
  List<AuditLog> findByTimestampBetween(Instant from, Instant to);

  /** Finds audit entries by action type and date range. */
  List<AuditLog> findByActionTypeAndTimestampBetween(
      AuditActionType actionType, Instant from, Instant to);
}
