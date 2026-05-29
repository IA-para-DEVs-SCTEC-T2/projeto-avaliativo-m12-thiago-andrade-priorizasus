# Capacity Model — Implementation Tasks

> **Feature**: Capacity Model — Weekly Slot creation, lifecycle, and state machine
> **Spec**: `.specs/features/capacity-model/spec.md`
> **Design**: `.specs/features/capacity-model/design.md`
> **Dependencies**: `patient-master` (needs Patient entity for FK on Slot)
> **Depended on by**: scoring-algorithm, booking-system, staff-dashboard
> **Canonical terms**: CONTEXT.md — Slot, BATCH Slot, Slot Status, Week, Occupancy

---

## Task Dependency Graph

```
Task-CM-01 (Slot Entity + SlotStatus + SlotType enums)    ← Depends on PM-01, PM-02
    ↓
Task-CM-02 (WeekConfig + Clinic schedule config)           ← Depends on PM-01
Task-CM-03 (SlotRepository + pessimistic lock queries)     ← Depends on CM-01
    ↓
Task-CM-04 (CapacityService — createWeeklySlots)           ← Depends on CM-01, CM-02, CM-03
Task-CM-05 (CapacityService — state machine transitions)   ← Depends on CM-04
Task-CM-06 (Slot expiração job — Friday 5 PM)              ← Depends on CM-05
    ↓
Task-CM-07 (SlotAuditLog entity + trigger)                 ← Depends on CM-01
Task-CM-08 (CapacityController — query APIs)               ← Depends on CM-04, CM-05
Task-CM-09 (Integration tests)                             ← Depends on CM-01 through CM-08
```

---

## Tasks

### Task-CM-01: Slot Entity, SlotStatus Enum, SlotType Enum

- **REQ**: CM-001 (Slot structure), CM-003 (BATCH type), CM-004 (State machine states)
- **What**: Create the `Slot` JPA entity and supporting enums.
  - `SlotStatus` enum: `AVAILABLE`, `RESERVED`, `BOOKED`, `CANCELLED`, `EXPIRED`.
    - `COMPLETED` is NOT a Slot status — it's an Appointment status (see booking-system).
  - `SlotType` enum: `BATCH` (only type in Phase 1 per ADR-0002).
  - `Slot` entity fields:
    - `id` (Long, PK, auto-increment)
    - `weekStart` (LocalDate, `@NotNull` — identifies the Week this Slot belongs to, per GRILL-DECISIONS #8)
    - `slotDateTime` (Instant, UTC, `@NotNull` — the actual date+time of the 30-min window)
    - `durationMinutes` (int, constant `30`)
    - `type` (SlotType, default `BATCH`)
    - `status` (SlotStatus, default `AVAILABLE`)
    - `patient` (`@ManyToOne(fetch = LAZY)`, FK → `patients.id`, nullable — null when AVAILABLE/EXPIRED)
    - `createdAt`, `updatedAt` (Instant, UTC)
  - Table: `slots` (snake_case)
  - **Unique constraint**: `(weekStart, slotDateTime)` — no two Slots at the same time.
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/SlotStatus.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/SlotType.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/Slot.java`
- **Tests**: Entity-level tests covered in repository tests.
- **Verification**:
  - [ ] `SlotStatus` has exactly: `AVAILABLE`, `RESERVED`, `BOOKED`, `CANCELLED`, `EXPIRED`
  - [ ] `SlotType` has `BATCH` (only value in Phase 1)
  - [ ] `slotDateTime` uses `Instant` (UTC)
  - [ ] `weekStart` uses `LocalDate`
  - [ ] FK to `patients.id` nullable
  - [ ] Table name `slots`
- **Depends on**: Task-PM-01 (PersistenceConfig), Task-PM-02 (Patient entity)

---

### Task-CM-02: WeekConfig — Clinic Schedule Configuration

- **REQ**: CM-002 (Clinic hours, working days)
- **What**: Create configuration for clinic operating schedule.
  - `WeekConfig` (`@ConfigurationProperties("clinic.schedule")`):
    - Properties in `application.properties`:
      ```properties
      clinic.schedule.days=MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY
      clinic.schedule.open-time=08:00
      clinic.schedule.close-time=17:00
      clinic.schedule.slot-duration-minutes=30
      clinic.schedule.slots-per-day=8
      clinic.schedule.total-weekly-slots=40
      clinic.schedule.morning-only=true
      ```
    - `getWorkingDays()` → `Set<DayOfWeek>` parsed from config
    - `getOpenTime()` / `getCloseTime()` → `LocalTime`
    - `getSlotDurationMinutes()` → 30
    - `getTotalWeeklySlots()` → 40
    - `isWorkingDay(LocalDate date)` → true if day in working days
  - `HolidayConfig` (`@ConfigurationProperties("clinic.holidays")`):
    - Properties: `clinic.holidays.dates=` (comma-separated list of `yyyy-MM-dd`)
    - `isHoliday(LocalDate date)` → true if date in holiday list
  - **No Slots created** for holidays or non-working days.
  - **Morning-only**: 8 Slots/day × 5 days = 40 (per design.md distribution).
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/config/WeekConfig.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/config/HolidayConfig.java`
  - Modificar: `src/main/resources/application.properties` (add clinic.schedule.* and clinic.holidays.*)
  - Create: `src/test/java/com/priorizasus/priorizasus/config/WeekConfigTest.java`
- **Tests**:
  - `WeekConfigTest`:
    - `getWorkingDays()` returns Mon–Fri
    - `isWorkingDay(SATURDAY)` → false
    - `isWorkingDay(MONDAY)` → true
    - `getSlotDurationMinutes()` → 30
    - `getTotalWeeklySlots()` → 40
  - HolidayConfig: `isHoliday(knownHoliday)` → true, `isHoliday(regularDay)` → false
- **Verification**:
  - [ ] Configuration loads from `application.properties`
  - [ ] `@ConfigurationProperties` annotated correctly
  - [ ] Holidays list empty by default (no hardcoded holidays)
- **Depends on**: Task-PM-01 (PersistenceConfig, ClinicTimeZone)

---

### Task-CM-03: SlotRepository with Pessimistic Lock Queries

- **REQ**: CM-001 (Slot queries), CM-003 (BATCH lookup), ADR-0001 (pessimistic locking)
- **What**: Create `SlotRepository` with standard queries AND pessimistic lock queries.
  - `SlotRepository extends JpaRepository<Slot, Long>`:
    - `List<Slot> findByWeekStart(LocalDate weekStart)` — all Slots for a Week
    - `List<Slot> findByWeekStartAndStatus(LocalDate weekStart, SlotStatus status)` — filtered by status
    - `List<Slot> findByWeekStartAndType(LocalDate weekStart, SlotType type)` — all BATCH slots
    - `Optional<Slot> findBySlotDateTime(Instant slotDateTime)` — unique lookup
    - `boolean existsByWeekStart(LocalDate weekStart)` — check if Slots already created
    - `long countByWeekStartAndStatus(LocalDate weekStart, SlotStatus status)` — occupancy counting
  - **Pessimistic Lock Queries** (ADR-0001 — `FOR UPDATE NOWAIT`):
    ```java
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "30000")})
    @Query("SELECT s FROM Slot s WHERE s.id = :id")
    Optional<Slot> lockSlotForUpdate(@Param("id") Long id);
    ```
    - Used by booking-system for single-slot lock during booking confirmation.
    ```java
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "30000")})
    @Query("SELECT s FROM Slot s WHERE s.weekStart = :weekStart AND s.type = 'BATCH' ORDER BY s.slotDateTime")
    List<Slot> lockBatchSlotsForUpdate(@Param("weekStart") LocalDate weekStart);
    ```
    - Used by scoring-algorithm to lock all 40 BATCH Slots for Weekly Selection.
  - **CRITICAL**: Pessimistic locks ONLY in repository — never in services. Services call these methods and handle `PessimisticLockException`.
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/repository/SlotRepository.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/repository/SlotRepositoryTest.java`
- **Tests**:
  - `SlotRepositoryTest` (`@DataJpaTest`, H2):
    - Save and retrieve by `weekStart`
    - `findByWeekStartAndStatus` filters correctly
    - `existsByWeekStart` true/false
    - `countByWeekStartAndStatus` returns correct count
    - `lockSlotForUpdate` acquires lock on single slot (verify no exception)
    - `lockBatchSlotsForUpdate` returns 40 slots (verify size)
- **Verification**:
  - [ ] All query methods compile and return correct results
  - [ ] Lock methods annotated with `@Lock(PESSIMISTIC_WRITE)`
  - [ ] Lock timeout hint set to 30000ms
  - [ ] `lockBatchSlotsForUpdate` returns Slots ordered by `slotDateTime`
- **Depends on**: Task-CM-01 (Slot entity)

---

### Task-CM-04: CapacityService — Weekly Slot Creation

- **REQ**: CM-001 (Weekly Slot Creation), CM-002 (Clinic hours), CM-003 (BATCH allocation)
- **What**: Create `CapacityService` with scheduled slot creation.
  1. `createWeeklySlots(LocalDate weekStart)`:
     - Computes Monday of the given week (if `weekStart` is not a Monday, adjusts backward)
     - Generates 40 BATCH Slots following design.md distribution:
       - Monday–Friday, 8 slots/day (08:00, 08:30, 09:00, 09:30, 10:00, 10:30, 11:00, 11:30)
       - Total: 8 × 5 = 40 all-BATCH
     - For each slot:
       - `weekStart = monday`
       - `slotDateTime = day.atTime(hour, minute) → Instant at clinicZone`
       - `type = BATCH`, `status = AVAILABLE`
     - Skips holidays and non-working days via `WeekConfig.isWorkingDay()` + `HolidayConfig.isHoliday()`
     - Saves all 40 Slots via `SlotRepository.saveAll()`
     - Logs: "40 slots created for week starting {weekStart}"
     - **Idempotent**: If `slotRepository.existsByWeekStart(weekStart)` → skip creation, log warning.
  2. `@Scheduled(cron = "0 0 7 * * MON")` — triggers every Monday 7 AM (clinic timezone).
     - The `@Scheduled` annotation must be on a method in a `@Service` or dedicated `@Component`.
     - Uses `ClinicTimeZone` to anchor the schedule.
  3. Enables scheduling via `@EnableScheduling` on `PRIORIZASUSApplication` (or a dedicated `@Configuration` class).
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/service/CapacityService.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/config/SchedulingConfig.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/service/CapacityServiceTest.java`
- **Tests**:
  - `CapacityServiceTest` (mock `SlotRepository`, `WeekConfig`, `HolidayConfig`):
    - `createWeeklySlots(Monday)` creates exactly 40 Slots
    - All Slots have `type = BATCH`, `status = AVAILABLE`
    - First slot: Monday 08:00, last slot: Friday 11:30
    - `slotDateTime` correctly converted to UTC Instant
    - All Slots have correct `weekStart` = Monday
    - Idempotent: second call with same weekStart → no-op
    - Holiday skip: if Wednesday is a holiday → 32 Slots created (Mon, Tue, Thu, Fri)
    - All-Week holiday: clinic closed → 0 Slots, no error
- **Verification**:
  - [ ] `@Service` annotation
  - [ ] `createWeeklySlots()` is `@Transactional`
  - [ ] `@Scheduled` cron expression: `0 0 7 * * MON`
  - [ ] `@EnableScheduling` configured
  - [ ] Slots distributed correctly (8/day × 5 days)
  - [ ] UTC conversion correct for slot times
- **Depends on**: Task-CM-01 (Slot entity), Task-CM-02 (WeekConfig, HolidayConfig), Task-CM-03 (SlotRepository)

---

### Task-CM-05: CapacityService — State Machine Transitions

- **REQ**: CM-004 (Slot Availability State Machine)
- **What**: Add state transition methods to `CapacityService`, enforcing the state machine defined in design.md.
  - `transitionSlot(Slot slot, SlotStatus newStatus, Long patientId, String reason)`:
    - Validates transition is legal per state machine:
      ```
      AVAILABLE → RESERVED  (Weekly Selection allocates to Patient)
      RESERVED  → BOOKED    (Patient confirms Booking)
      RESERVED  → EXPIRED   (Friday 5 PM, unconfirmed)
      Any*      → CANCELLED (Patient/Staff cancel; reverts to AVAILABLE)
      BOOKED    → COMPLETED (not a Slot status — handled via Appointment)
      ```
    - *CANCELLED reachable from AVAILABLE (Slot creation error), RESERVED, or BOOKED.
    - **Illegal transitions** (throw `IllegalStateException`):
      - BOOKED → AVAILABLE (must go through CANCELLED)
      - EXPIRED → anything (terminal)
      - AVAILABLE → BOOKED (must go through RESERVED)
    - Updates `slot.status`, `slot.patient` (if applicable), `updatedAt`
    - Logs to `SlotAuditLog` (see Task-CM-07) + `AuditLog`
  - `cancelSlot(Slot slot, String reason)`:
    - Convenience method: `transitionSlot(slot, CANCELLED, null, reason)`
    - After cancel: `slot.patient = null`, `slot.status = AVAILABLE` (via CANCELLED → AVAILABLE re-creation)
    - Actually: CANCELLED is a transient state. The slot is recreated as AVAILABLE.
    - Per ADR-0002 and GRILL-DECISIONS #2: CANCELLED → new Slot AVAILABLE (same time).
  - `expireReservations(LocalDate weekStart)`:
    - Finds all Slots with `status = RESERVED` and `weekStart = given`
    - Transitions each: `RESERVED → EXPIRED`
    - Logs: "N RESERVED slots expired for week {weekStart}"
- **Where**:
  - Modificar: `src/main/java/com/priorizasus/priorizasus/service/CapacityService.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/service/SlotStateMachineTest.java`
- **Tests**:
  - `SlotStateMachineTest`:
    - AVAILABLE → RESERVED: valid
    - RESERVED → BOOKED: valid
    - RESERVED → EXPIRED: valid
    - AVAILABLE → CANCELLED → new AVAILABLE: valid (re-creation)
    - BOOKED → CANCELLED → new AVAILABLE: valid
    - AVAILABLE → BOOKED: throws `IllegalStateException` (must go through RESERVED)
    - EXPIRED → anything: throws `IllegalStateException` (terminal)
    - BOOKED → AVAILABLE: throws `IllegalStateException` (must go through CANCELLED)
    - `expireReservations`: 3 RESERVED → 3 EXPIRED, 37 others unchanged
- **Verification**:
  - [ ] All legal transitions work
  - [ ] All illegal transitions throw
  - [ ] CANCELLED re-creates AVAILABLE Slot
  - [ ] EXPIRED is terminal (no further transitions)
  - [ ] `expireReservations()` is `@Transactional`
- **Depends on**: Task-CM-04 (CapacityService exists), Task-CM-01 (SlotStatus enum)

---

### Task-CM-06: Reservation Expiration Job (Friday 5 PM)

- **REQ**: CM-004 (RESERVED → EXPIRED on Friday 5 PM), GRILL-DECISIONS #3
- **What**: Scheduled job to expire unconfirmed RESERVED Slots.
  1. `@Scheduled(cron = "0 0 17 * * FRI")` method in `CapacityService`:
     - Calls `expireReservations(currentWeekStart)` where `currentWeekStart` = Monday of current week
     - Logs result: "N RESERVED slots expired for week {weekStart}"
     - If no RESERVED slots exist → no-op, log info
  2. **Important**: Expired Slots are NOT reusable (terminal state). Occupancy drops.
  3. **Phase 2 consideration**: Auto-release or carryover if occupancy < threshold (out of Phase 1 scope).
- **Where**:
  - Modificar: `src/main/java/com/priorizasus/priorizasus/service/CapacityService.java` (add `@Scheduled` method)
  - Modificar: `src/test/java/com/priorizasus/priorizasus/service/CapacityServiceTest.java` (add expiration tests)
- **Tests**:
  - `CapacityServiceTest`:
    - `expireReservations`: 5 RESERVED → all 5 set to EXPIRED
    - `expireReservations`: 0 RESERVED → no-op, no error
    - `expireReservations` for past week (idempotent): RESERVED already EXPIRED → no change
    - Verify AuditLog entries created with action type `WEEKLY_SELECTION` (or dedicated type)
- **Verification**:
  - [ ] Scheduled cron: `0 0 17 * * FRI`
  - [ ] Only RESERVED slots affected
  - [ ] EXPIRED slots cannot be re-booked (validated in state machine)
  - [ ] Idempotent (running twice doesn't double-expire)
- **Depends on**: Task-CM-05 (state machine), Task-CM-04 (CapacityService)

---

### Task-CM-07: SlotAuditLog — Slot State Change Logging

- **REQ**: CM-006 (Slot Auditing)
- **What**: Create `SlotAuditLog` entity to track every Slot state transition.
  - `SlotAuditLog` entity:
    - `id` (Long, PK)
    - `slot` (`@ManyToOne`, FK → `slots.id`)
    - `oldStatus` (SlotStatus, nullable — null on creation)
    - `newStatus` (SlotStatus, `@NotNull`)
    - `patientId` (Long, nullable — Patient who caused/benefited from change)
    - `staffUser` (String, default "system" for Phase 1)
    - `reason` (String, nullable)
    - `timestamp` (Instant, UTC, `@PrePersist`)
  - `SlotAuditLogRepository extends JpaRepository<SlotAuditLog, Long>`:
    - `List<SlotAuditLog> findBySlotId(Long slotId)`
    - `List<SlotAuditLog> findBySlot_WeekStart(LocalDate weekStart)`
    - `List<SlotAuditLog> findByTimestampBetween(Instant from, Instant to)`
  - **Integration**: Every `transitionSlot()` call in `CapacityService` creates a `SlotAuditLog` entry.
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/SlotAuditLog.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/repository/SlotAuditLogRepository.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/repository/SlotAuditLogRepositoryTest.java`
- **Tests**:
  - `SlotAuditLogRepositoryTest` (`@DataJpaTest`):
    - Persist log entry with slot reference
    - Query by slot ID, weekStart, date range
    - Verify `timestamp` auto-generated
- **Verification**:
  - [ ] Log entry created on every `transitionSlot()` call
  - [ ] `oldStatus` and `newStatus` recorded correctly
  - [ ] FK to `slots.id` configured
  - [ ] Queryable by slot, week, date range
- **Depends on**: Task-CM-01 (Slot entity), Task-CM-05 (state machine)

---

### Task-CM-08: CapacityController — Capacity Query APIs

- **REQ**: CM-005 (Capacity Query & Availability Check)
- **What**: Create REST controller for capacity/availability queries.
  - `CapacityController` (`@RestController`, `@RequestMapping("/api/capacity")`):
    - `GET /api/capacity/week/{weekStart}`:
      - Returns all 40 Slots for the Week with current status
      - Response: `List<SlotDTO>` with id, dateTime (local display), type, status, patientId
    - `GET /api/capacity/available?patientId={id}`:
      - Returns Slots viewable by this Patient:
        - `RESERVED` Slots where `slot.patient.id == patientId` → label `RESERVED_FOR_ME` (bookable)
        - `RESERVED` Slots where `slot.patient.id != patientId` → label `RESERVED_FOR_ANOTHER` (🔒)
        - Excludes `BOOKED`, `EXPIRED` slots
      - Response: `List<SlotVisibilityDTO>` with id, dateTime, status, visibility label
    - `GET /api/capacity/occupancy?weekStart={date}`:
      - Returns: `{ totalSlots: 40, bookedCount: N, cancelledCount: N, expiredCount: N, utilizationPercent: N }`
  - **DTOs**:
    - `SlotDTO`: id, slotDateTime (formatted BRT), type, status, patientId (nullable)
    - `SlotVisibilityDTO`: extends SlotDTO with `visibility` field: `RESERVED_FOR_ME`, `RESERVED_FOR_ANOTHER`, `AVAILABLE`
    - `OccupancyDTO`: totalSlots, bookedCount, cancelledCount, expiredCount, utilizationPercent
  - **Time conversion**: `slot.slotDateTime` (UTC Instant) → display in `America/Sao_Paulo` via `ClinicTimeZone`
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/controller/CapacityController.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/SlotDTO.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/SlotVisibilityDTO.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/OccupancyDTO.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/controller/CapacityControllerTest.java`
- **Tests**:
  - `CapacityControllerTest` (MockMvc):
    - `GET /api/capacity/week/2026-05-27` → 200, returns 40 Slots
    - `GET /api/capacity/week/invalid-date` → 400
    - `GET /api/capacity/available?patientId=1` → 200, correct visibility labels
    - `GET /api/capacity/occupancy?weekStart=2026-05-27` → 200, correct counts
- **Verification**:
  - [ ] Controller has NO business logic
  - [ ] Controller has NO `@Transactional`
  - [ ] Timezone conversion applied to `slotDateTime` display
  - [ ] `RESERVED_FOR_ME` / `RESERVED_FOR_ANOTHER` labels correct
- **Depends on**: Task-CM-04 (CapacityService), Task-CM-05 (state machine)

---

### Task-CM-09: Capacity Model Integration Tests

- **REQ**: CM-001 through CM-006 (full feature verification)
- **What**: Integration tests for the complete capacity workflow.
  1. `CapacityModelIntegrationTest` (`@SpringBootTest` + H2):
     - Full workflow: create Slots → verify 40 BATCH → transition a Slot to RESERVED → transition to BOOKED → cancel → verify AVAILABLE
     - Expiration: create Slots → transition 3 to RESERVED → trigger `expireReservations()` → verify 3 EXPIRED, 37 unchanged
     - Occupancy calculation: 34 BOOKED + 3 CANCELLED + 3 EXPIRED → 85% utilization
     - Idempotent creation: call `createWeeklySlots()` twice → only 40 Slots created
  2. `SlotStateMachineIntegrationTest` (`@SpringBootTest`):
     - All legal transitions (6 transitions from design.md)
     - All illegal transitions throw
     - EXPIRED terminal enforcement
  3. Update `SpecConsistencyTest.REQUIRED_SPEC_FILES` to include `tasks.md`.
- **Where**:
  - Create: `src/test/java/com/priorizasus/priorizasus/integration/CapacityModelIntegrationTest.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/integration/SlotStateMachineIntegrationTest.java`
  - Modificar: `src/test/java/com/priorizasus/priorizasus/harness/SpecConsistencyTest.java`
- **Verification**:
  - [ ] Full workflow passes
  - [ ] All state transitions tested
  - [ ] Occupancy calculation correct
  - [ ] `SpecConsistencyTest` updated
- **Depends on**: Task-CM-01 through CM-08

---

## Cross-Cutting Concerns

Same as patient-master — see patient-master/tasks.md § Cross-Cutting Concerns.
**Additional**:
- **Pessimistic locks ONLY in repositories** (ADR-0001, copilot-instructions.md)
- **`FOR UPDATE NOWAIT`** — fail-fast, 30s timeout; handle `PessimisticLockException` in service layer
- **40 All-BATCH** (ADR-0002) — no WALK_IN or other Slot types in Phase 1

---

**Status**: Ready for implementation
**Created**: May 24, 2026
