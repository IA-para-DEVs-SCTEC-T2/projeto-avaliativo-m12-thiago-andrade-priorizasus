# Booking System — Implementation Tasks

> **Feature**: Booking System — Patient confirms RESERVED Slots, creating Appointments with pessimistic locking
> **Spec**: `.specs/features/booking-system/spec.md`
> **Design**: `.specs/features/booking-system/design.md` (state machine, lock flow, concurrency scenarios)
> **Dependencies**: patient-master, capacity-model, scoring-algorithm
> **Depended on by**: staff-dashboard
> **Canonical terms**: CONTEXT.md — Booking (verb), Appointment, Reservation, RESERVED, BOOKED, Release

---

## Task Dependency Graph

```
Task-BK-01 (Appointment entity + AppointmentStatus enum)    ← Depends on PM-02, CM-01
    ↓
Task-BK-02 (AppointmentRepository + booking queries)        ← Depends on BK-01
Task-BK-03 (BookingService — validation chain)              ← Depends on BK-02, PM-04, CM-05, SA-04
    ↓
Task-BK-04 (BookingService — lockAndBook)                   ← Depends on BK-03, CM-03 (lockSlotForUpdate)
Task-BK-05 (BookingService — cancelAppointment)             ← Depends on BK-04
    ↓
Task-BK-06 (BookingController + DTOs)                       ← Depends on BK-04, BK-05
Task-BK-07 (Slot visibility service + API)                  ← Depends on BK-02, SA-05
Task-BK-08 (Booking constraints tests — concurrency)        ← Depends on BK-04
Task-BK-09 (Integration tests)                              ← Depends on BK-01 through BK-08
```

---

## Tasks

### Task-BK-01: Appointment Entity & AppointmentStatus Enum

- **REQ**: BK-004 (Appointment Creation)
- **What**: Create the `Appointment` JPA entity and `AppointmentStatus` enum.
  - `AppointmentStatus` enum: `CONFIRMED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`.
    - `NO_SHOW` is defined but Phase 2 — Phase 1 only marks past CONFIRMED Appointments for staff review.
  - `Appointment` entity:
    - `id` (Long, PK)
    - `patient` (`@ManyToOne`, FK → `patients.id`, `@NotNull`)
    - `slot` (`@OneToOne`, FK → `slots.id`, `@NotNull`, unique — one Appointment per Slot)
    - `status` (AppointmentStatus, default `CONFIRMED`)
    - `weekStart` (LocalDate — denormalized from Slot for fast "1 per Week" queries)
    - `confirmedAt` (Instant, UTC — when Patient clicked "Confirm")
    - `completedAt` (Instant, UTC, nullable — when staff marks COMPLETED)
    - `cancelledAt` (Instant, UTC, nullable — when cancelled)
    - `cancellationReason` (String, nullable)
    - `notesFromConsultation` (String, nullable — populated on COMPLETED)
    - `createdAt`, `updatedAt` (Instant, UTC)
  - Table: `appointments`
  - **Unique constraint**: `slot_id` (one Appointment per Slot — enforced at DB level)
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/AppointmentStatus.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/Appointment.java`
- **Tests**: Covered in repository + service tests.
- **Verification**:
  - [ ] `AppointmentStatus` has: `CONFIRMED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`
  - [ ] `@OneToOne` on `slot` field
  - [ ] `weekStart` denormalized for query performance
  - [ ] `slot_id` unique constraint
- **Depends on**: Task-PM-02 (Patient entity), Task-CM-01 (Slot entity)

---

### Task-BK-02: AppointmentRepository

- **REQ**: BK-002 (Validation queries), BK-006 (1-per-Week constraint)
- **What**: Create `AppointmentRepository` with queries for booking validation.
  - `AppointmentRepository extends JpaRepository<Appointment, Long>`:
    - `List<Appointment> findByPatientId(Long patientId)` — all appointments for a Patient
    - `List<Appointment> findByPatientIdAndStatus(Long patientId, AppointmentStatus status)` — filter by status
    - `Optional<Appointment> findBySlotId(Long slotId)` — unique lookup
    - **CRITICAL for BK-006**: `long countByPatientIdAndWeekStartAndStatus(Long patientId, LocalDate weekStart, AppointmentStatus status)` — counts BOOKED Appointments for 1-per-Week rule.
      - Used with `status = CONFIRMED` (or potentially `COMPLETED` — only active bookings count).
    - `boolean existsByPatientIdAndWeekStartAndStatus(Long patientId, LocalDate weekStart, AppointmentStatus status)` — fast existence check
    - `List<Appointment> findBySlot_WeekStart(LocalDate weekStart)` — all appointments for a Week
    - `List<Appointment> findByStatusAndSlot_SlotDateTimeBefore(AppointmentStatus status, Instant cutoff)` — find past CONFIRMED appointments for no-show review (Phase 1 flagging)
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/repository/AppointmentRepository.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/repository/AppointmentRepositoryTest.java`
- **Tests**:
  - `AppointmentRepositoryTest` (`@DataJpaTest`):
    - Save and retrieve by patient ID
    - `countByPatientIdAndWeekStartAndStatus` returns correct count
    - `existsByPatientIdAndWeekStartAndStatus` true/false
    - `findBySlotId` returns correct appointment
    - Unique constraint violation on duplicate slot
- **Verification**:
  - [ ] All queries compile and return correct results
  - [ ] `countByPatientIdAndWeekStartAndStatus` distinct from `existsBy...`
  - [ ] `slot_id` unique constraint enforced at DB level
- **Depends on**: Task-BK-01 (Appointment entity)

---

### Task-BK-03: BookingService — Validation Chain (No Locks)

- **REQ**: BK-002 (Booking Request & Validation), GRILL-DECISIONS #7 (1-BOOKED-per-week)
- **What**: Create `BookingService` with `validateBooking(Long patientId, Long slotId)` method.
  - Validation chain in strict order per design.md (fail-fast, cheapest checks first):
    1. **Patient exists & ACTIVE**: `patientRepository.findById(patientId)` → exists and `status == ACTIVE`
       - INACTIVE or SUSPENDED → throw `IneligibleForBookingException` ("Your account is not active")
    2. **Not already BOOKED this Week** (GRILL-DECISIONS #7):
       - `appointmentRepository.countByPatientIdAndWeekStartAndStatus(patientId, weekStart, CONFIRMED) == 0`
       - ONLY counts CONFIRMED Appointments — RESERVED Slots do NOT block
       - If ≥1 → throw `DuplicateAppointmentException` ("You already have an Appointment this Week")
    3. **Slot exists, not EXPIRED, not BOOKED**:
       - `slotRepository.findById(slotId)` → status not EXPIRED, not BOOKED
       - If EXPIRED → "This Slot has expired"
       - If already BOOKED → "Slot already booked" (pessimistic lock will also catch this)
    4. **BATCH Slot: Patient must be in Weekly Selection**:
       - If `slot.type == BATCH`:
         - Slot must have `status == RESERVED` AND `slot.patient.id == patientId`
         - If RESERVED for another → `RESERVED_FOR_ANOTHER` → throw `IneligibleForBookingException` ("This Slot is reserved for another Patient")
         - If slot not RESERVED for this Patient → check if Patient is in current Weekly Selection
         - Implementation: query `SelectionRepository` for the current Week to verify Patient rank ≤40
    5. **Slot time not in past**:
       - `slot.slotDateTime.isAfter(Instant.now())` → future slot only
       - If past → throw `InvalidBookingException` ("Cannot book Appointment in the past")
  - **Return**: Validated `Patient` and `Slot` objects (ready for locking in BK-04).
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/service/BookingService.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/exception/IneligibleForBookingException.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/exception/DuplicateAppointmentException.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/exception/InvalidBookingException.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/service/BookingValidationTest.java`
- **Tests**:
  - `BookingValidationTest`:
    - All 5 validations pass → returns validated Patient + Slot
    - Patient INACTIVE → `IneligibleForBookingException`
    - Patient SUSPENDED → `IneligibleForBookingException`
    - Patient already has 1 CONFIRMED Appointment this Week → `DuplicateAppointmentException`
    - Patient has 1 RESERVED but 0 CONFIRMED → validation PASSES (RESERVED doesn't count)
    - Slot EXPIRED → `InvalidBookingException`
    - Slot RESERVED for OTHER Patient → `IneligibleForBookingException`
    - Slot RESERVED for THIS Patient → validation PASSES
    - Slot dateTime in past → `InvalidBookingException`
- **Verification**:
  - [ ] Validation chain order: Patient → 1-per-Week → Slot status → BATCH eligibility → time check
  - [ ] RESERVED slots do NOT count toward 1-per-Week limit
  - [ ] Each validation failure returns distinct HTTP-appropriate exception
  - [ ] No locks acquired during validation
- **Depends on**: Task-BK-02 (AppointmentRepository), Task-PM-04 (PatientRepository), Task-CM-03 (SlotRepository), Task-SA-04 (Selection records)

---

### Task-BK-04: BookingService — Lock & Book (Pessimistic Lock Flow)

- **REQ**: BK-003 (Pessimistic Locking During Booking), ADR-0001
- **What**: Add `confirmBooking(Long patientId, Long slotId)` method to `BookingService`.
  - Following the design.md booking sequence diagram:
    ```java
    @Transactional
    public Appointment confirmBooking(Long patientId, Long slotId) {
        // Step 1: Validate (BK-03 — no locks)
        Patient patient = ...; // from validation
        Slot slot = ...;       // from validation
        
        // Step 2: Acquire pessimistic lock on Slot
        Slot lockedSlot;
        try {
            lockedSlot = slotRepository.lockSlotForUpdate(slotId)
                .orElseThrow(() -> new InvalidBookingException("Slot not found"));
        } catch (PessimisticLockException e) {
            throw new SlotLockException("Slot temporarily locked, try another");
        }
        
        // Step 3: Double-check status after lock (concurrent modification)
        if (lockedSlot.getStatus() != SlotStatus.RESERVED
            || !lockedSlot.getPatient().getId().equals(patientId)) {
            throw new SlotLockException("Slot state changed, try another");
        }
        
        // Step 4: Mutate (lock held)
        lockedSlot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(lockedSlot);
        
        // Step 5: Create Appointment
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setSlot(lockedSlot);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setWeekStart(lockedSlot.getWeekStart());
        appointment.setConfirmedAt(Instant.now());
        appointment = appointmentRepository.save(appointment);
        
        // Step 6: Update Selection status
        selectionRepository.findByWeeklySelection_WeekStartAndPatientId(
            lockedSlot.getWeekStart(), patientId)
            .ifPresent(sel -> {
                sel.setStatus(SelectionStatus.BOOKED);
                selectionRepository.save(sel);
            });
        
        // Step 7: Audit log
        auditLogRepository.save(new AuditLog(BOOKING, ...));
        
        return appointment;
    }
    ```
  - Lock timeout: `NOWAIT` → fail immediately if locked. Patient UI shows "Slot temporarily locked, try another" (409 Conflict).
  - Transaction commits → lock released automatically.
- **Where**:
  - Modificar: `src/main/java/com/priorizasus/priorizasus/service/BookingService.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/exception/SlotLockException.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/service/BookingConfirmationTest.java`
- **Tests**:
  - `BookingConfirmationTest`:
    - Happy path: Patient books RESERVED Slot → Appointment CONFIRMED, Slot BOOKED
    - Lock failure: `PessimisticLockException` → `SlotLockException` thrown, transaction rolled back
    - Double-check failure: after lock, slot status changed to BOOKED → `SlotLockException`
    - Appointment created with correct `weekStart`, `confirmedAt`
    - Selection status updated to `BOOKED`
    - Audit log entry created with action type `BOOKING`
    - Verify `@Transactional` — if any step fails, everything rolls back
- **Verification**:
  - [ ] Pessimistic lock acquired via `lockSlotForUpdate()`
  - [ ] Double-check after lock (prevents TOCTOU race)
  - [ ] `SlotLockException` maps to HTTP 409 in controller
  - [ ] Appointment created with `CONFIRMED` status
  - [ ] Slot transitions: `RESERVED → BOOKED`
  - [ ] Selection status: `SELECTED → BOOKED`
- **Depends on**: Task-BK-03 (validation chain), Task-CM-03 (lockSlotForUpdate), Task-BK-02 (AppointmentRepository), Task-SA-04 (SelectionRepository)

---

### Task-BK-05: BookingService — Cancel Appointment

- **REQ**: BK-005 (Booking Cancellation)
- **What**: Add `cancelAppointment(Long appointmentId, Long patientId, String reason)` method.
  1. Validates Appointment exists and belongs to this Patient
  2. Validates Appointment status is `CONFIRMED` (cannot cancel COMPLETED/CANCELLED)
  3. Validates cancellation timing:
     - Any time allowed in Phase 1
     - If <24 hours before Appointment: warn "Late cancellation" but allow (staff follows up)
  4. Updates Appointment: `status = CANCELLED`, `cancelledAt = Instant.now()`, `cancellationReason = reason`
  5. **Releases Slot**: `capacityService.transitionSlot(slot, CANCELLED, patientId, reason)` → Slot reverts to AVAILABLE (via CANCELLED → new AVAILABLE)
  6. Updates Selection: `status = RELEASED`
  7. Logs audit: `CANCELLATION` action type
  - **Distinct from Staff Release** (SD-001): Staff Release uses a different endpoint and logs `RELEASE` action type. Patient cancellation uses `CANCELLATION`.
- **Where**:
  - Modificar: `src/main/java/com/priorizasus/priorizasus/service/BookingService.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/service/BookingCancellationTest.java`
- **Tests**:
  - `BookingCancellationTest`:
    - Cancel CONFIRMED Appointment → Appointment CANCELLED, Slot reverts to AVAILABLE
    - Cancel COMPLETED Appointment → throws exception (cannot cancel past)
    - Cancel CANCELLED Appointment → throws exception (idempotent reject)
    - Cancel Appointment not owned by Patient → throws exception (security)
    - Cancel <24h before → warning logged, cancellation succeeds
    - Selection status updated to `RELEASED`
    - Audit log entry created
- **Verification**:
  - [ ] Slot reverts to AVAILABLE after cancellation
  - [ ] Appointment CANCELLED with timestamp + reason
  - [ ] Selection status → `RELEASED`
  - [ ] Cannot double-cancel
  - [ ] Staff Release (distinct endpoint/log type) handled in staff-dashboard
- **Depends on**: Task-BK-04 (confirmBooking), Task-CM-05 (transitionSlot)

---

### Task-BK-06: BookingController + DTOs

- **REQ**: BK-001 (Slot Visibility), BK-002 (Booking Request), BK-005 (Cancellation)
- **What**: Create REST controller for Patient booking portal.
  - `BookingController` (`@RestController`, `@RequestMapping("/api/booking")`):
    - `GET /api/booking/my-available-slots?patientId={id}`:
      - Returns Slots with visibility labels (BK-001)
      - Delegates to `BookingService.getAvailableSlots(patientId)`
    - `GET /api/booking/my-appointments?patientId={id}`:
      - Returns Patient's current + past Appointments
    - `POST /api/booking/reserve`:
      - Request body: `{ patientId, slotId }`
      - Calls `bookingService.confirmBooking(patientId, slotId)`
      - Returns `AppointmentDTO` on success
      - Maps exceptions:
        - `IneligibleForBookingException` → HTTP 403
        - `DuplicateAppointmentException` → HTTP 400
        - `InvalidBookingException` → HTTP 400
        - `SlotLockException` → HTTP 409
    - `DELETE /api/booking/appointments/{appointmentId}?patientId={id}`:
      - Calls `bookingService.cancelAppointment(appointmentId, patientId, reason)`
      - Returns confirmation DTO
  - **DTOs**:
    - `BookingRequestDTO`: patientId, slotId
    - `AppointmentDTO`: id, patientId, patientName, slotDateTime (BRT), status, weekStart
    - `SlotVisibilityDTO`: (already created in CM-08 — reuse)
    - `CancellationRequestDTO`: reason (optional string)
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/controller/BookingController.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/BookingRequestDTO.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/AppointmentDTO.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/CancellationRequestDTO.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/controller/BookingControllerTest.java`
- **Tests**:
  - `BookingControllerTest` (MockMvc):
    - `POST /api/booking/reserve` valid → 200, returns AppointmentDTO
    - `POST /api/booking/reserve` patient INACTIVE → 403
    - `POST /api/booking/reserve` already BOOKED this week → 400
    - `POST /api/booking/reserve` slot locked → 409
    - `POST /api/booking/reserve` slot for another Patient → 403
    - `DELETE /api/booking/appointments/1` → 200, confirmation
    - `GET /api/booking/my-available-slots?patientId=1` → 200, returns labeled slots
    - `GET /api/booking/my-appointments?patientId=1` → 200
- **Verification**:
  - [ ] Controller has NO business logic
  - [ ] Controller has NO `@Transactional`
  - [ ] Exception → HTTP status mapping correct
  - [ ] `SlotVisibilityDTO` reused from capacity-model
- **Depends on**: Task-BK-04 (confirmBooking), Task-BK-05 (cancelAppointment)

---

### Task-BK-07: Slot Visibility Service (Labeled Visibility)

- **REQ**: BK-001 (Labeled Visibility), GRILL-DECISIONS #13
- **What**: Add `getAvailableSlots(Long patientId)` to `BookingService` (or dedicated method).
  - Logic:
    1. Get current Week's `weekStart`
    2. Query all BATCH Slots for the current Week
    3. For each Slot:
       - If `slot.status == RESERVED && slot.patient.id == patientId` → label `RESERVED_FOR_ME` (BOOKABLE)
       - If `slot.status == RESERVED && slot.patient.id != patientId` → label `RESERVED_FOR_ANOTHER` (🔒)
       - If `slot.status == BOOKED` → exclude
       - If `slot.status == EXPIRED` → exclude
       - If `slot.status == AVAILABLE` → label `AVAILABLE` (only if Patient is in Weekly Selection)
    4. Return list with visibility labels per design.md API response format.
  - **Transparency**: Non-selected Patients see all 40 Slots as `RESERVED_FOR_ANOTHER` — they know the system is full but fair.
- **Where**:
  - Modificar: `src/main/java/com/priorizasus/priorizasus/service/BookingService.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/service/SlotVisibilityTest.java`
- **Tests**:
  - `SlotVisibilityTest`:
    - Selected Patient (rank 5) sees Slot RESERVED to them → `RESERVED_FOR_ME`
    - Selected Patient sees Slot RESERVED to other → `RESERVED_FOR_ANOTHER`
    - Non-selected Patient (rank 45) sees all RESERVED → `RESERVED_FOR_ANOTHER`
    - BOOKED slots excluded from response
    - EXPIRED slots excluded from response
- **Verification**:
  - [ ] Labels follow GRILL-DECISIONS #13 spec
  - [ ] Non-selected Patients can see Slots exist (transparency) but can't book
  - [ ] Selected Patients only see `RESERVED_FOR_ME` on their own Slots
- **Depends on**: Task-BK-02 (AppointmentRepository), Task-SA-04 (Selection records)

---

### Task-BK-08: Concurrency Tests (Double-Booking Prevention)

- **REQ**: BK-003 (Pessimistic Lock), ADR-0001
- **What**: Dedicated concurrency tests validating NOWAIT lock behavior.
  1. `BookingConcurrencyTest`:
     - **Two Patients, same Slot**:
       - Thread 1: Patient A books Slot 5 → acquires lock, succeeds (200)
       - Thread 2: Patient B books Slot 5 → NOWAIT fails → `SlotLockException` (409)
       - Verify only 1 Appointment created for Slot 5
     - **Patient booking while Weekly Selection runs**:
       - Thread 1: Weekly Selection locks all 40 BATCH Slots
       - Thread 2: Patient tries booking Slot 5 → NOWAIT timeout → 409
       - After Weekly Selection commits → Patient retries, succeeds
     - **10 concurrent Patient bookings for different Slots**:
       - 10 threads, each booking a different Slot
       - All 10 succeed (no interference)
  2. Uses `ExecutorService` with multiple threads, H2 in-memory DB.
  3. Verifies zero double-bookings: count of BOOKED Slots == count of successful bookings.
- **Where**:
  - Create: `src/test/java/com/priorizasus/priorizasus/concurrency/BookingConcurrencyTest.java`
- **Tests**: (self-contained, described above)
- **Verification**:
  - [ ] Zero double-bookings under concurrent access
  - [ ] NOWAIT fails fast (no waiting, no deadlocks)
  - [ ] All 10 parallel different-slot bookings succeed
  - [ ] Weekly Selection lock blocks Patient booking correctly
- **Depends on**: Task-BK-04 (confirmBooking), Task-BK-02 (AppointmentRepository)

---

### Task-BK-09: Booking System Integration Tests

- **REQ**: BK-001 through BK-006 (full booking workflow)
- **What**: End-to-end integration tests.
  1. `BookingIntegrationTest` (`@SpringBootTest` + H2):
     - Full happy path: Patient selected in Weekly Selection → sees RESERVED_FOR_ME → books → Appointment CONFIRMED → Slot BOOKED
     - Validation chain: each validation failure returns correct HTTP status
     - Cancellation: book → cancel → Slot AVAILABLE again → re-booked by another Patient
     - 1-per-Week: book Monday → try booking Tuesday → rejected (400)
     - RESERVED (not BOOKED) doesn't block: has RESERVED Monday → can still book another RESERVED Tuesday
     - Past slot rejection
  2. Update `SpecConsistencyTest.REQUIRED_SPEC_FILES`.
- **Where**:
  - Create: `src/test/java/com/priorizasus/priorizasus/integration/BookingIntegrationTest.java`
  - Modificar: `src/test/java/com/priorizasus/priorizasus/harness/SpecConsistencyTest.java`
- **Verification**:
  - [ ] Full booking lifecycle works
  - [ ] All validations enforced
  - [ ] Pessimistic lock prevents double-booking
  - [ ] Cancellation releases Slot
  - [ ] 1-per-Week respected
- **Depends on**: Task-BK-01 through BK-08

---

## Cross-Cutting Concerns

Same as previous features.
**Additional**:
- **Pessimistic lock NOWAIT** (ADR-0001): Single slot lock for <100ms during booking confirmation.
- **Lock ONLY in repository** (`lockSlotForUpdate`), service handles `PessimisticLockException`.
- **HTTP 409 Conflict** for lock failures — Patient UI retries.
- **1-BOOKED-per-Week** (GRILL-DECISIONS #7): RESERVED does NOT count. Only CONFIRMED Appointments counted.
- **RESERVED_FOR_ME / RESERVED_FOR_ANOTHER** labels (GRILL-DECISIONS #13).

---

**Status**: Ready for implementation
**Created**: May 24, 2026
