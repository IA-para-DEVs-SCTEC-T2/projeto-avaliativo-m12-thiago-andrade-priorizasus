package com.priorizasus.priorizasus.repository;

import com.priorizasus.priorizasus.entity.Slot;
import com.priorizasus.priorizasus.entity.SlotStatus;
import com.priorizasus.priorizasus.entity.SlotType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data repository for {@link Slot} entities. */
@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {

  List<Slot> findByWeekStart(LocalDate weekStart);

  List<Slot> findByWeekStartAndStatus(LocalDate weekStart, SlotStatus status);

  List<Slot> findByWeekStartAndType(LocalDate weekStart, SlotType type);

  boolean existsByWeekStart(LocalDate weekStart);

  long countByWeekStartAndStatus(LocalDate weekStart, SlotStatus status);

  /**
   * Pessimistic lock on a single Slot with NOWAIT (fail fast if already locked). Used during
   * Booking to prevent double-booking per ADR-0001.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "30000")})
  @Query("SELECT s FROM Slot s WHERE s.id = :id")
  Optional<Slot> lockSlotForUpdate(@Param("id") Long id);

  /**
   * Pessimistic lock on all BATCH Slots for a given Week with NOWAIT. Used by the Weekly Selection
   * to atomically reserve all 40 BATCH Slots per ADR-0001. Slots are ordered by {@code
   * slotDateTime} for deterministic 1:1 mapping to ranked Patients.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "30000")})
  @Query(
      "SELECT s FROM Slot s WHERE s.weekStart = :weekStart AND s.type = 'BATCH' ORDER BY s.slotDateTime")
  List<Slot> lockBatchSlotsForUpdate(@Param("weekStart") LocalDate weekStart);
}
