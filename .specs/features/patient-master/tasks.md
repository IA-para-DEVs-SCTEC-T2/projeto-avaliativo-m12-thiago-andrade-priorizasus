# Patient Master — Implementation Tasks

> **Feature**: Patient Master Data (Foundation)
> **Spec**: `.specs/features/patient-master/spec.md`
> **Dependencies**: None — this is the foundation feature
> **Depended on by**: All other Phase 1 features (capacity-model, scoring-algorithm, booking-system, staff-dashboard)
> **Canonical terms**: CONTEXT.md — Patient, Category, PatientStatus, targetDate, lastConsultationDate

---

## Task Dependency Graph

```
Task-PM-01 (Config + AuditLog + ClinicTimeZone)     ← Transversal, NO dependencies
    ↓
Task-PM-02 (Patient Entity + PatientStatus)           ← Depends on PM-01
    ↓
Task-PM-03 (Category Entity + enums)                  ← Depends on PM-01
    ↓
Task-PM-04 (PatientRepository + CategoryRepository)   ← Depends on PM-02, PM-03
    ↓
Task-PM-05 (PatientService — registration)            ← Depends on PM-01, PM-02, PM-04
Task-PM-06 (TargetDateCalculator)                     ← Depends on PM-01, PM-03
Task-PM-07 (CategoryService — assign + gestational)   ← Depends on PM-03, PM-04, PM-06
    ↓
Task-PM-08 (PatientService — status lifecycle)        ← Depends on PM-05, PM-07
Task-PM-09 (PatientService — lastConsultation update) ← Depends on PM-05, PM-06
    ↓
Task-PM-10 (PatientController + DTOs + Templates)     ← Depends on PM-05, PM-07, PM-08, PM-09
Task-PM-11 (Integration tests + data integrity)       ← Depends on PM-01 through PM-10
```

---

## Tasks

### Task-PM-01: Foundation — ClinicTimeZone, AuditLog Entity & Persistence Config

- **REQ**: PM-004 (audit logging), PM-006 (timezone), ADR-0003 (UTC storage), ADR-0005 (snapshot model)
- **What**: Create transversal infrastructure used by ALL Phase 1 features:
  1. `ClinicTimeZone` utility component — provides `ZoneId` for `America/Sao_Paulo`, converts between UTC and local display, and provides `LocalDate.now(clinicZone)` for duration calculations.
  2. `AuditLog` JPA entity — immutable log of all system actions (WEEKLY_SELECTION, BOOKING, REASSIGN, RELEASE, CANCELLATION, SUSPENSION, COMPLETED). Fields: `id`, `actionType` (enum), `timestamp` (Instant UTC), `staffUser` (String), `patientId` (Long nullable), `slotId` (Long nullable), `appointmentId` (Long nullable), `details` (String). `@PrePersist` sets timestamp to `Instant.now()`.
  3. `AuditActionType` enum — values: `WEEKLY_SELECTION`, `BOOKING`, `REASSIGN`, `RELEASE`, `CANCELLATION`, `SUSPENSION`, `COMPLETED`, `STATUS_CHANGE`.
  4. `AuditLogRepository` — extends `JpaRepository<AuditLog, Long>`. Query methods: `findByActionType`, `findByPatientId`, `findByTimestampBetween`.
  5. `PersistenceConfig` — `@Configuration` class declaring `ZoneId clinicZone()` bean (`America/Sao_Paulo`), and `Clock` bean set to UTC.
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/config/PersistenceConfig.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/config/ClinicTimeZone.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/AuditLog.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/AuditActionType.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/repository/AuditLogRepository.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/config/ClinicTimeZoneTest.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/repository/AuditLogRepositoryTest.java`
- **Tests**:
  - `ClinicTimeZoneTest`: UTC→BRT conversion correctness, `LocalDate.now(clinicZone)` vs `LocalDate.now(UTC)`, DST edge case (BRT doesn't observe DST since 2019, but verify).
  - `AuditLogRepositoryTest`: Persist and query by action type, date range, patient ID.
- **Verification**:
  - [ ] `ClinicTimeZone.getZone()` returns `ZoneId.of("America/Sao_Paulo")`
  - [ ] `AuditLog` entity persists with auto-generated timestamp
  - [ ] `AuditLogRepository` queries work correctly
  - [ ] `PersistenceConfig` loads without errors
  - [ ] ArchUnit: entities in `..entity..`, config in `..config..`
- **Depends on**: None — this is the first task to implement.

---

### Task-PM-02: Patient Entity & PatientStatus Enum

- **REQ**: PM-001 (Patient Registration), PM-006 (Data Integrity)
- **What**: Create the `Patient` JPA entity and `PatientStatus` enum.
  - `PatientStatus` enum: `ACTIVE`, `INACTIVE`, `SUSPENDED`.
  - `Patient` entity fields:
    - `id` (Long, auto-increment PK)
    - `name` (String, `@NotBlank`)
    - `cpf` (String, `@NotBlank`, `@Column(unique = true)`, 11 digits validated via `@Pattern`)
    - `birthDate` (LocalDate, `@NotNull`, `@Past` — cannot be future)
    - `phone` (String, optional — invalid format warns but doesn't block per spec)
    - `address` (String, optional)
    - `status` (PatientStatus, default `ACTIVE`)
    - `registrationDate` (LocalDate, `@PrePersist` sets to `LocalDate.now(clinicZone)`)
    - `lastConsultationDate` (LocalDate, nullable — no consultation yet)
    - `createdAt` (Instant, UTC, `@PrePersist`)
    - `updatedAt` (Instant, UTC, `@PreUpdate`)
  - Table: `patients` (snake_case)
  - **No business logic** beyond basic Bean Validation annotations.
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/PatientStatus.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/Patient.java`
- **Tests**: Entity-level tests are covered in later tasks (repository + service tests).
- **Verification**:
  - [ ] `PatientStatus` enum has exactly 3 values: `ACTIVE`, `INACTIVE`, `SUSPENDED`
  - [ ] CPF annotated `@Column(unique = true)`
  - [ ] `birthDate` annotated `@Past`
  - [ ] All timestamps use `Instant` (UTC), not `LocalDateTime`
  - [ ] `lastConsultationDate` and `registrationDate` use `LocalDate` (per ADR-0003)
  - [ ] Table name `patients` in snake_case
- **Depends on**: Task-PM-01 (PersistenceConfig must exist)

---

### Task-PM-03: Category Entity, CategoryType & ChildMilestone Enums

- **REQ**: PM-002 (Patient Categorization)
- **What**: Create the `Category` JPA entity and supporting enums.
  - `CategoryType` enum: `PRENATAL`, `CHILD`, `CHRONIC`.
  - `ChildMilestone` enum: `DAY_7`, `DAY_30`, `MONTH_2`, `MONTH_4`, `MONTH_6`, `MONTH_9`, `MONTH_12`, `MONTH_18`, `MONTH_24`, `ANNUAL`. Each value has `daysFromBirth` (int) for `targetDate` calculation.
  - `Category` entity fields:
    - `id` (Long, PK)
    - `patient` (`@ManyToOne(fetch = LAZY)`, FK → `patients.id`)
    - `type` (CategoryType, `@NotNull`)
    - `active` (boolean, default `true`)
    - `ultrasoundDate` (LocalDate, nullable — PRENATAL only)
    - `gestationalWeeksAtUltrasound` (Integer, nullable — PRENATAL only)
    - `lastMilestoneCompleted` (ChildMilestone, nullable — CHILD only)
    - `conditionDescription` (String, nullable — CHRONIC only, e.g., "Diabetes tipo 2")
    - `targetDate` (LocalDate, nullable — calculated, stored for query performance)
    - `createdAt`, `updatedAt` (Instant, UTC)
  - Table: `patient_categories` (snake_case)
  - **Constraint**: A Patient can have many active Categories simultaneously (e.g., PRENATAL + CHRONIC).
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/CategoryType.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/ChildMilestone.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/Category.java`
- **Tests**: Entity-level tests covered in repository/service tests.
- **Verification**:
  - [ ] `CategoryType` has exactly: `PRENATAL`, `CHILD`, `CHRONIC`
  - [ ] `ChildMilestone` enum has 10 values with correct `daysFromBirth`
  - [ ] `Category` table is `patient_categories`
  - [ ] FK to `patients.id` configured correctly
  - [ ] `ultrasoundDate` + `gestationalWeeksAtUltrasound` only populated for PRENATAL
  - [ ] `lastMilestoneCompleted` only populated for CHILD
- **Depends on**: Task-PM-01 (PersistenceConfig), Task-PM-02 (Patient entity needed for FK)

---

### Task-PM-04: PatientRepository & CategoryRepository

- **REQ**: PM-001 (Registration queries), PM-002 (Category queries), PM-006 (CPF unique lookup)
- **What**: Create Spring Data JPA repositories following STACK.md repository pattern.
  - `PatientRepository extends JpaRepository<Patient, Long>`:
    - `Optional<Patient> findByCpf(String cpf)` — duplicate CPF check
    - `Optional<Patient> findByIdAndStatus(Long id, PatientStatus status)` — active patient lookup
    - `List<Patient> findByStatus(PatientStatus status)` — filter by lifecycle
    - `List<Patient> findByStatusAndCategories_ActiveTrue(PatientStatus status)` — eligible patients with active categories
    - `@Query("SELECT p FROM Patient p WHERE p.lastConsultationDate < :cutoff OR p.lastConsultationDate IS NULL")` — find patients due for consultation
    - `boolean existsByCpf(String cpf)` — fast duplicate check
  - `CategoryRepository extends JpaRepository<Category, Long>`:
    - `List<Category> findByPatientIdAndActiveTrue(Long patientId)` — active categories for a patient
    - `List<Category> findByTypeAndActiveTrue(CategoryType type)` — all patients with a given category
    - `@Query("SELECT c FROM Category c WHERE c.patient.status = 'ACTIVE' AND c.active = true")` — eligible categories for Weekly Selection
  - **CRITICAL**: No `FOR UPDATE` queries yet — pessimistic locking belongs in capacity-model and booking-system features.
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/repository/PatientRepository.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/repository/CategoryRepository.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/repository/PatientRepositoryTest.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/repository/CategoryRepositoryTest.java`
- **Tests**:
  - `PatientRepositoryTest` (H2, `@DataJpaTest`):
    - Save and find by CPF
    - `existsByCpf` returns true/false
    - Find by status filters correctly
    - `findByStatusAndCategories_ActiveTrue` returns only patients with active categories
    - CPF unique constraint violation on duplicate insert
  - `CategoryRepositoryTest` (H2, `@DataJpaTest`):
    - Save category linked to patient
    - `findByPatientIdAndActiveTrue` returns only active
    - Multi-category patient: PRENATAL + CHRONIC
- **Verification**:
  - [ ] All repository methods compile and execute correctly against H2
  - [ ] CPF unique constraint enforced at DB level
  - [ ] `findByStatusAndCategories_ActiveTrue` joins correctly
  - [ ] Test uses `@DataJpaTest` with `application-test.properties` (H2 in-memory)
- **Depends on**: Task-PM-02 (Patient entity), Task-PM-03 (Category entity)

---

### Task-PM-05: PatientService — Registration

- **REQ**: PM-001 (Patient Registration)
- **What**: Create `PatientService` with `registerPatient(PatientDTO dto)` method.
  - **Validation** (before persistence):
    1. CPF format: 11 digits via regex `\d{11}` — reject if invalid
    2. CPF uniqueness: `patientRepository.existsByCpf(cpf)` → throw `DuplicatePatientException` if true
    3. Birth date: must not be in the future (`dto.birthDate.isAfter(LocalDate.now(clinicZone))`) → reject
    4. Phone: warn if invalid format but allow save per spec
  - **Persistence**:
    1. Map DTO → Patient entity
    2. Set `status = ACTIVE`, `registrationDate = LocalDate.now(clinicZone)`
    3. Save via repository
    4. Log audit: `AUDIT_ACTION_TYPE.PATIENT_REGISTERED` (hardcoded staff user "system" for Phase 1)
    5. Return created Patient ID
  - **Read operations**:
    - `getPatientById(Long id)` → returns `Optional<Patient>`
    - `getPatientByCpf(String cpf)` → returns `Optional<Patient>`
    - `getAllActivePatients()` → returns `List<Patient>`
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/service/PatientService.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/PatientDTO.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/exception/DuplicatePatientException.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/exception/InvalidPatientDataException.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/service/PatientServiceTest.java`
- **Tests**:
  - `PatientServiceTest` (JUnit 5 + Mockito, mock repositories):
    - `registerPatient` happy path: valid CPF, non-future birth → patient saved with ACTIVE status
    - `registerPatient` duplicate CPF → throws `DuplicatePatientException`
    - `registerPatient` future birth date → throws `InvalidPatientDataException`
    - `registerPatient` invalid CPF format (10 digits, letters) → throws `InvalidPatientDataException`
    - `registerPatient` with invalid phone → saves successfully (warn-only)
    - `getPatientById` returns patient
    - `getAllActivePatients` returns only ACTIVE patients
- **Verification**:
  - [ ] `@Service` annotation on class
  - [ ] `@Transactional` on `registerPatient()`
  - [ ] CPF validation: 11 digits enforced
  - [ ] Birth date not future enforced
  - [ ] Audit log entry created on registration
  - [ ] `PatientDTO` has fields: name, cpf, birthDate, phone, address (all validation annotated)
  - [ ] `DuplicatePatientException` and `InvalidPatientDataException` extend `RuntimeException`
- **Depends on**: Task-PM-01 (AuditLog, ClinicTimeZone), Task-PM-02 (Patient entity), Task-PM-04 (PatientRepository)

---

### Task-PM-06: TargetDateCalculator Service

- **REQ**: PM-003 (Target Date Calculation)
- **What**: Create standalone `TargetDateCalculator` service with `calculateTargetDate(Category category, Patient patient)` method.
  - **PRENATAL** (dynamic, uses `ClinicTimeZone`):
    ```java
    long daysSinceUltrasound = ChronoUnit.DAYS.between(
        category.getUltrasoundDate(), LocalDate.now(clinicZone));
    double weeksElapsed = daysSinceUltrasound / 7.0;
    double currentWeeks = category.getGestationalWeeksAtUltrasound() + weeksElapsed;
    if (currentWeeks >= 36): targetDate = today + 7 days
    else if (currentWeeks >= 28): targetDate = today + 15 days
    else: targetDate = today + 30 days
    ```
  - **CHILD** (milestone-based, per GRILL-DECISIONS #10):
    ```java
    ChildMilestone nextMilestone = getNextMilestone(category.getLastMilestoneCompleted());
    targetDate = patient.getBirthDate().plusDays(nextMilestone.getDaysFromBirth());
    ```
    `getNextMilestone(ChildMilestone current)`: iterate enum values in order, return next.
    If `lastMilestoneCompleted` is null (newborn), start at `DAY_7`.
  - **CHRONIC** (60-day interval):
    ```java
    targetDate = (lastConsultationDate != null)
        ? lastConsultationDate.plusDays(60)
        : LocalDate.now(clinicZone).plusDays(60);
    ```
  - **Multi-category**: Each Category has its own `targetDate` — they are NOT merged.
  - **daysOverdue helper**: `calculateDaysOverdue(LocalDate targetDate)`:
    ```java
    return (int) Math.max(0, ChronoUnit.DAYS.between(targetDate, LocalDate.now(clinicZone)));
    ```
    Count starts the day AFTER targetDate (per GRILL-DECISIONS #1).
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/service/TargetDateCalculator.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/service/TargetDateCalculatorTest.java`
- **Tests**:
  - `TargetDateCalculatorTest` — parameterized tests (JUnit 5 `@ParameterizedTest`):
    - **PRENATAL**: Given `ultrasoundDate`, `gestationalWeeksAtUltrasound`, `today`, verify:
      - <28w → `targetDate = today + 30`
      - 28w to <36w → `targetDate = today + 15`
      - 36w+ → `targetDate = today + 7`
      - Boundary: exactly 28w0d → 15 days; exactly 36w0d → 7 days
    - **CHILD**: Given `birthDate`, `lastMilestoneCompleted`, verify:
      - null → next is DAY_7; targetDate = birthDate + 7
      - DAY_7 → next is DAY_30; targetDate = birthDate + 30
      - DAY_30 → next is MONTH_2; targetDate = birthDate + ~60
      - MONTH_24 → next is ANNUAL; targetDate = birthDate + 365
      - All 10 milestones chain correctly
    - **CHRONIC**: Given `lastConsultationDate`:
      - Has date → targetDate = date + 60
      - Null → targetDate = today + 60
    - **daysOverdue**: On targetDate → 0; 1 day after → 1; 5 days after → 5; 1 day before → 0 (not overdue)
- **Verification**:
  - [ ] `@Service` annotation on class
  - [ ] All calculations use `LocalDate` and `ChronoUnit.DAYS` (not `Duration.between()`)
  - [ ] `getNextMilestone()` covers all 10 milestones + null → DAY_7
  - [ ] All parametrized tests pass
- **Depends on**: Task-PM-01 (ClinicTimeZone), Task-PM-03 (Category entity, enums)

---

### Task-PM-07: CategoryService — Assign Category & Gestational Calculation

- **REQ**: PM-002 (Patient Categorization), PM-003 (Target Date)
- **What**: Create `CategoryService` with:
  1. `assignCategory(Long patientId, CategoryAssignmentDTO dto)`:
     - Validates Patient exists and is ACTIVE or SUSPENDED
     - If PRENATAL: requires `ultrasoundDate` + `gestationalWeeksAtUltrasound`, validates `ultrasoundDate` is not future
     - If CHILD: requires `birthDate` on Patient, sets `lastMilestoneCompleted = null` (newborn default)
     - If CHRONIC: requires `conditionDescription`
     - Creates Category entity, calls `TargetDateCalculator.calculateTargetDate()`, sets `targetDate` field
     - Saves via `CategoryRepository`
  2. `getActiveCategories(Long patientId)` → returns active categories
  3. `deactivateCategory(Long categoryId)` → sets `active = false`
  4. `calculateCurrentGestationalWeeks(Category prenatalCategory)` → for PRENATAL dynamic weight calculation
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/service/CategoryService.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/CategoryAssignmentDTO.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/service/CategoryServiceTest.java`
- **Tests**:
  - `CategoryServiceTest`:
    - `assignCategory` PRENATAL: requires ultrasound data, calculates targetDate, saves
    - `assignCategory` CHILD: sets initial milestone null → DAY_7, calculates targetDate
    - `assignCategory` CHRONIC: requires conditionDescription, calculates targetDate
    - `assignCategory` multiple categories on same patient: PRENATAL + CHRONIC
    - `assignCategory` patient not found → throws exception
    - `deactivateCategory`: sets active=false, targetDate cleared
    - `calculateCurrentGestationalWeeks`: 24w at ultrasound 14 days ago → ~26w
- **Verification**:
  - [ ] `@Service` + `@Transactional` annotations
  - [ ] PRENATAL category requires `ultrasoundDate` and `gestationalWeeksAtUltrasound`
  - [ ] `targetDate` stored on Category after assignment
  - [ ] Multiple active categories per patient supported
- **Depends on**: Task-PM-03 (Category entity), Task-PM-04 (CategoryRepository), Task-PM-06 (TargetDateCalculator)

---

### Task-PM-08: PatientService — Status Lifecycle

- **REQ**: PM-004 (Patient Status Lifecycle)
- **What**: Add status lifecycle methods to `PatientService`:
  1. `suspendPatient(Long patientId, String reason)`:
     - Validates Patient exists and is ACTIVE
     - Sets `status = SUSPENDED`
     - Logs audit: `SUSPENSION` action type with reason
     - Patient retains existing Appointments; excluded from future Weekly Selections
  2. `reactivatePatient(Long patientId)`:
     - Validates Patient exists and is SUSPENDED
     - Sets `status = ACTIVE`
     - Logs audit: `STATUS_CHANGE` action type
  3. `deactivatePatient(Long patientId, String reason)`:
     - Validates Patient exists and is not already INACTIVE
     - Sets `status = INACTIVE`
     - Logs audit: `STATUS_CHANGE` action type with reason
     - **INACTIVE is permanent** — Patient data retained for audit only
  4. `getEligiblePatients()`:
     - Returns all Patients with `status = ACTIVE` AND at least one active Category
     - Used by scoring-algorithm's Weekly Selection query
- **Where**:
  - Modificar: `src/main/java/com/priorizasus/priorizasus/service/PatientService.java` (add methods)
  - Create: `src/test/java/com/priorizasus/priorizasus/service/PatientStatusLifecycleTest.java`
- **Tests**:
  - `PatientStatusLifecycleTest`:
    - Suspend ACTIVE patient → status SUSPENDED, audit logged
    - Suspend INACTIVE patient → throws exception
    - Reactivate SUSPENDED patient → status ACTIVE
    - Reactivate ACTIVE patient → throws exception
    - Deactivate ACTIVE patient → status INACTIVE, audit logged
    - Deactivate INACTIVE patient → throws exception (idempotent reject)
    - `getEligiblePatients`: only ACTIVE + has active Category
    - SUSPENDED patient NOT returned in `getEligiblePatients()`
- **Verification**:
  - [ ] All status transitions logged via `AuditLogRepository`
  - [ ] `suspendPatient()` only works on ACTIVE patients
  - [ ] `reactivatePatient()` only works on SUSPENDED patients
  - [ ] `deactivatePatient()` only works on non-INACTIVE patients
  - [ ] `getEligiblePatients()` filters correctly
- **Depends on**: Task-PM-05 (PatientService exists), Task-PM-07 (CategoryService for eligibility check)

---

### Task-PM-09: PatientService — Update Last Consultation

- **REQ**: PM-005 (Last Consultation Tracking)
- **What**: Add `updateLastConsultation(Long patientId, LocalDate consultationDate)` method to `PatientService`:
  1. Validates Patient exists and is ACTIVE or SUSPENDED
  2. Sets `lastConsultationDate = consultationDate`
  3. For each active Category, recalculates `targetDate` via `TargetDateCalculator`
  4. Saves updated Patient and Categories
  5. Logs audit: `STATUS_CHANGE` action type with details "lastConsultationDate updated to {date}"
  - **Trigger**: Called when staff marks an Appointment as COMPLETED (SD-003 in staff-dashboard). For now, the method exists independently.
- **Where**:
  - Modificar: `src/main/java/com/priorizasus/priorizasus/service/PatientService.java` (add method)
  - Create: `src/test/java/com/priorizasus/priorizasus/service/PatientConsultationUpdateTest.java`
- **Tests**:
  - `PatientConsultationUpdateTest`:
    - Update `lastConsultationDate` for PRENATAL patient: targetDate recalculated to today+7/15/30 depending on weeks
    - Update for CHILD patient: targetDate advances to next milestone
    - Update for CHRONIC patient: targetDate = today + 60
    - Update for multi-category patient (PRENATAL + CHRONIC): both targetDates recalculated independently
    - Update for SUSPENDED patient: allowed (suspension doesn't block consultation recording)
    - Update for INACTIVE patient: throws exception
- **Verification**:
  - [ ] `lastConsultationDate` updated on Patient
  - [ ] `targetDate` recalculated on ALL active Categories
  - [ ] Audit log entry created
  - [ ] Method is `@Transactional`
- **Depends on**: Task-PM-05 (PatientService), Task-PM-06 (TargetDateCalculator), Task-PM-07 (CategoryService)

---

### Task-PM-10: PatientController + DTOs + Thymeleaf Templates

- **REQ**: PM-001 (Registration form), PM-002 (Category assignment form)
- **What**: Create MVC controller and server-rendered Thymeleaf templates per STACK.md.
  - `PatientController` (`@Controller`, not `@RestController` — Thymeleaf SSR):
    - `GET /patients/register` → shows registration form (`patient/register.html`)
    - `POST /patients/register` → processes form, validates, calls `PatientService.registerPatient()`, redirects to category assignment or shows errors
    - `GET /patients/{id}` → patient detail view
    - `GET /patients/{id}/assign-category` → shows category assignment form (`patient/assign-category.html`)
    - `POST /patients/{id}/assign-category` → calls `CategoryService.assignCategory()`
    - `GET /patients?status=ACTIVE&category=PRENATAL` → filtered patient list
  - **DTOs**:
    - `PatientDTO`: name, cpf, birthDate, phone, address (with `@NotBlank`, `@Pattern`, `@Past` Bean Validation)
    - `CategoryAssignmentDTO`: categoryType, ultrasoundDate, gestationalWeeksAtUltrasound, conditionDescription
    - `PatientViewDTO`: read-only projection for list/detail views (id, name, cpf, status, categories, targetDate, daysOverdue)
  - **Controller advice**: `GlobalExceptionHandler` (`@ControllerAdvice`) returning Thymeleaf error fragments:
    - `DuplicatePatientException` → 409 Conflict
    - `InvalidPatientDataException` → 400 Bad Request
    - General `Exception` → 500
  - **Thymeleaf templates** (in `src/main/resources/templates/patient/`):
    - `register.html` — form with fields: name, CPF (masked input), birth date, phone, address. Validation errors displayed inline. On success, redirects to `/patients/{id}/assign-category`.
    - `assign-category.html` — form with Category type dropdown. If PRENATAL selected: show ultrasound date + gestational weeks fields. If CHRONIC: show condition description. On submit, success message + link to patient detail.
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/controller/PatientController.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/PatientDTO.java` (if not created in PM-05)
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/CategoryAssignmentDTO.java` (if not created in PM-07)
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/PatientViewDTO.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/exception/GlobalExceptionHandler.java`
  - Create: `src/main/resources/templates/patient/register.html`
  - Create: `src/main/resources/templates/patient/assign-category.html`
  - Create: `src/main/resources/templates/patient/detail.html`
  - Create: `src/test/java/com/priorizasus/priorizasus/controller/PatientControllerTest.java`
- **Tests**:
  - `PatientControllerTest` (MockMvc):
    - `GET /patients/register` → 200, form visible, CSRF token present
    - `POST /patients/register` with valid data → 302 redirect to `/patients/{id}/assign-category`
    - `POST /patients/register` with duplicate CPF → 409, error message in model
    - `POST /patients/register` with blank name → 400, validation error
    - `POST /patients/register` with future birth date → 400, validation error
    - `GET /patients/1/assign-category` → 200, dropdown with 3 Category types
    - `POST /patients/1/assign-category` PRENATAL with required fields → 200, success
    - `POST /patients/1/assign-category` PRENATAL without ultrasound → 400, error
- **Verification**:
  - [ ] Controller has NO business logic — delegates to services only
  - [ ] Controller has NO `@Transactional` annotation
  - [ ] Controller does NOT access repositories directly
  - [ ] `POST` endpoints include CSRF protection (Spring Security not needed — Thymeleaf auto)
  - [ ] All validation errors displayed in Thymeleaf templates with `th:errors`
  - [ ] ArchUnit: controller in `..controller..` package
- **Depends on**: Task-PM-05 (PatientService), Task-PM-07 (CategoryService), Task-PM-08, Task-PM-09

---

### Task-PM-11: Integration Tests & Data Integrity Validation

- **REQ**: PM-001 through PM-006 (full feature verification)
- **What**: End-to-end integration tests validating the complete patient-master workflow:
  1. `PatientMasterIntegrationTest` (`@SpringBootTest` + H2):
     - Full happy path: register patient → assign PRENATAL category → verify targetDate → suspend → reactivate
     - Full happy path with CHILD: register → assign CHILD → verify milestone progression → update lastConsultation → verify next milestone
     - Multi-category: register → assign PRENATAL + CHRONIC → verify both targetDates → deactivate one → verify other still active
     - CPF uniqueness: attempt duplicate → verify rejection at DB level
     - Patient status filtering: create ACTIVE, SUSPENDED, INACTIVE patients → query only ACTIVE
  2. `PatientDataIntegrityTest` (`@DataJpaTest`):
     - CPF unique constraint violation at DB level
     - `birthDate` not null constraint
     - `name` not blank constraint
     - Timestamps auto-populated (`createdAt`, `updatedAt`)
     - `registrationDate` auto-set on persist
  3. Update `SpecConsistencyTest.REQUIRED_SPEC_FILES` to include:
     - `".specs/features/patient-master/tasks.md"`
- **Where**:
  - Create: `src/test/java/com/priorizasus/priorizasus/integration/PatientMasterIntegrationTest.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/repository/PatientDataIntegrityTest.java`
  - Modificar: `src/test/java/com/priorizasus/priorizasus/harness/SpecConsistencyTest.java` (add tasks.md to required list)
- **Tests**: (see above — self-contained)
- **Verification**:
  - [ ] All integration tests pass (full workflow)
  - [ ] Data integrity tests pass (constraints, timestamps)
  - [ ] `SpecConsistencyTest` updated to validate `tasks.md` existence
  - [ ] JaCoCo coverage ≥75% on entity/service/controller packages for patient-master
- **Depends on**: Task-PM-01 through Task-PM-10 (all patient-master tasks)

---

## Cross-Cutting Concerns (Applicable to ALL Tasks)

| Concern | Rule | Reference |
|---------|------|-----------|
| **Terminology** | Use CONTEXT.md canonical terms ONLY: "Patient" not "Client", "Category" not "Condition", "targetDate" not "deadline" | CONTEXT.md |
| **Layering** | Controller → Service → Repository → Entity. No shortcuts. | copilot-instructions.md |
| **Transactions** | `@Transactional` ONLY on service public methods. NEVER on controllers. | ADR-0001, STACK.md |
| **Locks** | No pessimistic locks in patient-master (first needed in capacity-model) | ADR-0001 |
| **Timestamps** | `Instant` (UTC) for created_at/updated_at. `LocalDate` for consultation/dates. | ADR-0003 |
| **Null safety** | All public service methods return `Optional<T>`, never raw `null` | STACK.md |
| **Logging** | `private static final Logger log = LoggerFactory.getLogger(ClassName.class)` | STACK.md |
| **Tests** | One logical test per `@Test` method. Class name = `{Class}Test`. | STACK.md |
| **Phase 1 scope** | No home visits, no-show penalties, notifications, multi-user auth, mobile app | ADR-0004 |
| **No auth** | Hardcoded `staffUser = "system"` in audit logs until Phase 2 | ADR-0004 |

---

**Status**: Ready for implementation  
**Created**: May 24, 2026  
**Last Updated**: May 24, 2026
