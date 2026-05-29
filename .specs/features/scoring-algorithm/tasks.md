# Scoring Algorithm — Implementation Tasks

> **Feature**: Scoring Algorithm — Weekly Selection of top 40 Patients by clinical priority
> **Spec**: `.specs/features/scoring-algorithm/spec.md`
> **Design**: `.specs/features/scoring-algorithm/design.md` (pseudocode, lock strategy, entity diagrams)
> **Dependencies**: patient-master (Patients, Categories, targetDate), capacity-model (BATCH Slots + locks)
> **Depended on by**: booking-system, staff-dashboard
> **Canonical terms**: CONTEXT.md — Weekly Selection, Score, Category Weight, daysOverdue, Ranking, Selection

---

## Task Dependency Graph

```
Task-SA-01 (WeeklySelection + Selection entities)         ← Depends on PM-02, CM-01
    ↓
Task-SA-02 (ScoringService — eligibility + scoring)       ← Depends on SA-01, PM-04, PM-06, PM-07
Task-SA-03 (ScoringService — ranking + tie-breaking)      ← Depends on SA-02
    ↓
Task-SA-04 (ScoringService — executeWeeklySelection)      ← Depends on SA-03, CM-03 (lockBatchSlots)
    ↓
Task-SA-05 (SelectionResultDTO + transparency output)     ← Depends on SA-04
Task-SA-06 (Accumulated fairness + parametrized tests)    ← Depends on SA-04
Task-SA-07 (ScoringAlgorithmController / scheduled trigger) ← Depends on SA-04
Task-SA-08 (Integration tests — full Weekly Selection)    ← Depends on SA-01 through SA-07
```

---

## Tasks

### Task-SA-01: WeeklySelection & Selection Entities

- **REQ**: SA-004 (Weekly Selection recording), SA-005 (Transparency)
- **What**: Create entities that persist Weekly Selection results per the design.md entity diagram.
  - `WeeklySelection` entity:
    - `id` (Long, PK)
    - `weekStart` (LocalDate, `@NotNull`, unique — one Selection per Week)
    - `executedAt` (Instant, UTC)
    - `totalEligible` (int — total eligible Patients at snapshot time)
    - `totalSelected` (int — count of Patients ranked 1–40, max 40)
    - `status` (`WeeklySelectionStatus` enum: `COMPLETED`, `FAILED`)
    - `errorMessage` (String, nullable — reason if FAILED)
    - `staffUser` (String, default "system" for Phase 1)
    - `createdAt` (Instant, UTC)
    - `selections` (`@OneToMany(mappedBy = "weeklySelection", cascade = ALL)`) — list of individual Selection records
  - `Selection` entity:
    - `id` (Long, PK)
    - `weeklySelection` (`@ManyToOne`, FK → `weekly_selections.id`)
    - `patient` (`@ManyToOne`, FK → `patients.id`)
    - `score` (int — calculated Score)
    - `rank` (int — 1 to N, where N = totalEligible)
    - `slot` (`@ManyToOne`, FK → `slots.id`, nullable — null if not selected, i.e., rank > 40)
    - `status` (`SelectionStatus` enum: `SELECTED`, `BOOKED`, `RELEASED`)
      - `SELECTED`: Patient in top 40, Reservation created (Slot status RESERVED)
      - `BOOKED`: Patient confirmed Booking (Slot status BOOKED)
      - `RELEASED`: Staff override released this Selection (Slot returned to AVAILABLE)
    - `weightBreakdown` (String, JSON-format — per-Category weight details for auditability, e.g., `[{"type":"PRENATAL","weight":1000,"overdueDays":10,"bonus":100}]`)
    - `createdAt` (Instant, UTC)
  - `WeeklySelectionStatus` enum: `COMPLETED`, `FAILED`
  - `SelectionStatus` enum: `SELECTED`, `BOOKED`, `RELEASED`
  - Tables: `weekly_selections`, `selections` (snake_case)
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/WeeklySelectionStatus.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/SelectionStatus.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/WeeklySelection.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/entity/Selection.java`
- **Tests**: Covered in service tests.
- **Verification**:
  - [ ] `WeeklySelection` has `@OneToMany` cascade to `Selection`
  - [ ] `weekStart` is unique (one Selection per Week)
  - [ ] `Selection.rank` 1–40 have `slot != null`; rank 41+ have `slot == null`
  - [ ] `weightBreakdown` stores JSON for audit transparency
- **Depends on**: Task-PM-02 (Patient entity), Task-CM-01 (Slot entity)

---

### Task-SA-02: ScoringService — Eligibility Check & Score Calculation

- **REQ**: SA-001 (Eligibility Rules, Snapshot Model), SA-002 (Scoring Formula), GRILL-DECISIONS #5, #9
- **What**: Create `ScoringService` with eligibility and scoring logic. This is the CORE of the fairness algorithm.
  1. `isEligible(Patient patient, LocalDate weekStart)`:
     - **Snapshot model** (ADR-0005, GRILL-DECISIONS #9): evaluated ONCE at Monday 7 AM. Mid-week changes don't revoke eligibility.
     - Returns `true` if ALL conditions met:
       - `patient.status == ACTIVE`
       - At least one active Category (`categoryRepository.findByPatientIdAndActiveTrue(id)` not empty)
       - Not already BOOKED this Week: `appointmentRepository.countByPatientIdAndWeekStart(patientId, weekStart) == 0`
         - Counts only BOOKED Appointments, NOT RESERVED slots (GRILL-DECISIONS #7)
       - `lastConsultationDate` is ≥7 days ago OR null (never consulted): `ChronoUnit.DAYS.between(lastConsultation, today) >= 7`
     - Returns `false` if patient is INACTIVE, SUSPENDED, no Category, already BOOKED, consulted recently.
  2. `calculateScore(Patient patient, List<Category> activeCategories)`:
     - Per the spec formula and design.md pseudocode:
       ```java
       int totalWeight = 0;
       int totalBonus = 0;
       for (Category cat : activeCategories) {
           int weight = getCategoryWeight(cat, patient);
           int daysOverdue = targetDateCalculator.calculateDaysOverdue(cat.getTargetDate());
           int bonus = Math.min(daysOverdue * 10, 500); // capped per Category (GRILL-DECISIONS #5)
           totalWeight += weight;
           totalBonus += bonus;
       }
       return totalWeight + totalBonus;
       ```
  3. `getCategoryWeight(Category category, Patient patient)`:
     - **PRENATAL** (dynamic — recalculated each Weekly Selection, GRILL-DECISIONS #11):
       ```java
       double currentWeeks = categoryService.calculateCurrentGestationalWeeks(category);
       if (currentWeeks >= 36) return 1000;
       else if (currentWeeks >= 28) return 500;
       else return 300;
       ```
     - **CHILD** (milestone-based, GRILL-DECISIONS #10):
       ```java
       ChildMilestone nextMilestone = targetDateCalculator.getNextMilestone(category.getLastMilestoneCompleted());
       if (nextMilestone == DAY_7 || nextMilestone == DAY_30) return 900;
       else if (nextMilestone in [MONTH_2, MONTH_4, MONTH_6, MONTH_9, MONTH_12]) return 700;
       else return 400; // MONTH_18, MONTH_24, ANNUAL
       ```
     - **CHRONIC**: always `200`.
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/service/ScoringService.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/repository/WeeklySelectionRepository.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/repository/SelectionRepository.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/service/ScoringServiceTest.java`
- **Tests**:
  - `ScoringServiceTest`:
    - `isEligible`:
      - ACTIVE + PRENATAL + no BOOKED this week + lastConsultation 14 days ago → eligible
      - INACTIVE → not eligible
      - SUSPENDED → not eligible
      - No Category → not eligible
      - Already BOOKED this week → not eligible (RESERVED does NOT block)
      - lastConsultation 3 days ago → not eligible (<7 day gap)
      - lastConsultation null (never consulted) → eligible (no gap restriction)
    - `calculateScore`:
      - PRENATAL 36+w, 10 days overdue → weight=1000, bonus=100, score=1100
      - PRENATAL 36+w, 60 days overdue → weight=1000, bonus=500 (capped), score=1500
      - CHILD DAY_7, on time → weight=900, bonus=0, score=900
      - CHRONIC, 70 days overdue → weight=200, bonus=500 (capped), score=700
      - Multi-category (PRENATAL 36+w, 10d overdue + CHRONIC, 20d overdue) → (1000+200) + (100+200) = 1500
    - `getCategoryWeight`:
      - PRENATAL <28w → 300
      - PRENATAL 28-35.9w → 500
      - PRENATAL ≥36w → 1000
      - Boundary: exactly 28w0d → 500; exactly 35w6d → 500; exactly 36w0d → 1000
      - CHILD DAY_7 → 900
      - CHILD MONTH_12 → 700
      - CHILD ANNUAL → 400
- **Verification**:
  - [ ] `isEligible()` is a SNAPSHOT — no side effects, read-only
  - [ ] Days overdue capped at 500 PER Category (not total)
  - [ ] Multi-category weights and bonuses summed independently
  - [ ] CHILD weight keyed to NEXT milestone, not current
  - [ ] All parametrized tests pass
- **Depends on**: Task-PM-04 (PatientRepository, CategoryRepository), Task-PM-06 (TargetDateCalculator), Task-PM-07 (CategoryService), Task-SA-01 (entities)

---

### Task-SA-03: ScoringService — Ranking & Tie-Breaking

- **REQ**: SA-003 (Ranking & Selection)
- **What**: Add `rankPatients(List<ScoredPatient> scored)` method to `ScoringService`.
  1. Sort all scored Patients by:
     - Primary: Score DESC (highest first)
     - Secondary (tie-break): earliest `targetDate` ASC (across all Categories for that Patient)
     - Tertiary (tie-break): earliest `registrationDate` ASC (Patient ID ascending as proxy)
  2. Return ordered list with rank numbers (1 to N).
  3. `ScoredPatient` is a value record:
     ```java
     record ScoredPatient(Patient patient, int score, LocalDate earliestTargetDate,
                          List<CategoryScore> categoryScores) {}
     record CategoryScore(CategoryType type, int weight, int daysOverdue, int bonus) {}
     ```
- **Where**:
  - Modificar: `src/main/java/com/priorizasus/priorizasus/service/ScoringService.java` (add `rankPatients`)
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/ScoredPatient.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/CategoryScore.java`
  - Modificar: `src/test/java/com/priorizasus/priorizasus/service/ScoringServiceTest.java` (add ranking tests)
- **Tests**:
  - `ScoringServiceTest`:
    - 3 Patients with distinct scores → correct descending order
    - 2 Patients with identical score, different targetDate → earliest targetDate first
    - 2 Patients with identical score + targetDate → earliest registration first
    - Patient A: Score 1100, targetDate May 30 → rank 1
    - Patient B: Score 1100, targetDate May 28 → rank 1 (tie-break wins)
    - Patient C: Score 900 → rank 3
    - Verify rank numbers: 1, 2, 3... (no gaps, no ties at same rank)
- **Verification**:
  - [ ] Ranking deterministic: same input → same output
  - [ ] Tie-breaking consistent with spec
  - [ ] `ScoredPatient` record is immutable
- **Depends on**: Task-SA-02 (ScoringService, scoring logic)

---

### Task-SA-04: ScoringService — Execute Weekly Selection (Atomic)

- **REQ**: SA-004 (Weekly Selection Execution), ADR-0001 (pessimistic locking), GRILL-DECISIONS #12 (lock recovery)
- **What**: Add `executeWeeklySelection(LocalDate weekStart)` — the MAIN algorithm entry point.
  - **Atomic all-or-nothing transaction** per design.md pseudocode:
    ```java
    @Transactional
    public WeeklySelectionResult executeWeeklySelection(LocalDate weekStart) {
        // Step 1: Fetch all ACTIVE Patients with active Categories
        List<Patient> candidates = patientRepository.findByStatusAndCategories_ActiveTrue(ACTIVE);
        
        // Step 2: Filter eligible (snapshot)
        List<Patient> eligible = candidates.stream()
            .filter(p -> isEligible(p, weekStart))
            .toList();
        
        // Step 3: Score each eligible Patient
        List<ScoredPatient> scored = eligible.stream()
            .map(p -> new ScoredPatient(p, calculateScore(p, getActiveCategories(p)), ...))
            .toList();
        
        // Step 4: Rank
        List<ScoredPatient> ranked = rankPatients(scored);
        
        // Step 5: Select top 40
        List<ScoredPatient> top40 = ranked.stream().limit(40).toList();
        
        // Step 6: LOCK all 40 BATCH Slots (pessimistic)
        List<Slot> batchSlots;
        try {
            batchSlots = slotRepository.lockBatchSlotsForUpdate(weekStart);
        } catch (PessimisticLockException e) {
            // Rollback, log failure
            log.error("Weekly Selection failed: lock timeout for week {}", weekStart, e);
            auditLogRepository.save(new AuditLog(WEEKLY_SELECTION, "FAILED", ...));
            throw new WeeklySelectionFailedException("Lock timeout — retry", e);
        }
        
        // Step 7: RESERVE top 40 into Slots (1:1 mapping)
        WeeklySelection ws = new WeeklySelection(weekStart, COMPLETED, ...);
        ws = weeklySelectionRepository.save(ws);
        
        for (int i = 0; i < top40.size(); i++) {
            ScoredPatient sp = top40.get(i);
            Slot slot = batchSlots.get(i);
            capacityService.transitionSlot(slot, RESERVED, sp.patient().getId(), "Weekly Selection");
            
            Selection sel = new Selection(ws, sp.patient(), sp.score(), i + 1, slot, SELECTED, ...);
            selectionRepository.save(sel);
        }
        
        // Step 8: Record non-selected (rank 41+) as Selection with slot=null
        for (int i = 40; i < ranked.size(); i++) {
            ScoredPatient sp = ranked.get(i);
            Selection sel = new Selection(ws, sp.patient(), sp.score(), i + 1, null, SELECTED, ...);
            selectionRepository.save(sel);
        }
        
        // Step 9: Log success
        auditLogRepository.save(new AuditLog(WEEKLY_SELECTION, "COMPLETED",
            String.format("40 selected, %d waiting", ranked.size() - 40)));
        
        return new WeeklySelectionResult(ws, top40, ranked);
    }
    ```
  - **Lock timeout handling** (`PessimisticLockException`):
    - Catch, rollback transaction, log error, create FAILED WeeklySelection record.
    - Staff sees "FAILED: Lock timeout — retry" in dashboard (SD-001).
    - No partial results (transaction rolled back).
  - **Edge cases**:
    - <40 eligible → select all, remaining Slots stay AVAILABLE.
    - 0 eligible → no Slots reserved, WeeklySelection status COMPLETED with 0 selected.
    - Weekly Selection already exists for this `weekStart` → release previous Reservations, recalculate (re-run scenario).
- **Where**:
  - Modificar: `src/main/java/com/priorizasus/priorizasus/service/ScoringService.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/WeeklySelectionResult.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/exception/WeeklySelectionFailedException.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/service/WeeklySelectionExecutionTest.java`
- **Tests**:
  - `WeeklySelectionExecutionTest`:
    - 75 eligible Patients → top 40 selected, 35 waiting (rank 41–75 with slot=null)
    - Exactly 40 eligible → all selected, 0 waiting
    - 25 eligible → all 25 selected, 15 Slots remain AVAILABLE
    - 0 eligible → 0 selected, 40 Slots remain AVAILABLE, status COMPLETED (not FAILED)
    - Lock timeout simulation → `PessimisticLockException` caught, transaction rolled back, FAILED record
    - Re-run: existing WeeklySelection for same weekStart → previous Reservations released, new ones created
    - Verify `weightBreakdown` JSON populated correctly per Selection
- **Verification**:
  - [ ] `@Transactional` on `executeWeeklySelection()`
  - [ ] All-or-nothing: partial results never committed
  - [ ] `PessimisticLockException` handled gracefully
  - [ ] `lockBatchSlotsForUpdate()` called (from SlotRepository)
  - [ ] `WeeklySelection` + all `Selection` records persisted
  - [ ] Non-selected Patients have `Selection` records with `slot = null`
  - [ ] Audit log entry on success AND failure
- **Depends on**: Task-SA-03 (ranking), Task-CM-03 (SlotRepository.lockBatchSlotsForUpdate), Task-CM-05 (CapacityService.transitionSlot)

---

### Task-SA-05: SelectionResultDTO — Transparency Output

- **REQ**: SA-005 (Transparency & Auditability)
- **What**: Design DTO for Weekly Selection result visible to staff.
  - `SelectionResultDTO`:
    - `weekStart` (LocalDate)
    - `executedAt` (String, formatted BRT)
    - `totalEligible` (int)
    - `totalSelected` (int)
    - `status` (String: "COMPLETED" or "FAILED")
    - `errorMessage` (String, nullable)
    - `selectedPatients` (`List<PatientSelectionDTO>`) — rank 1–40:
      - `rank`, `patientId`, `patientName`, `score`, `slotDateTime`, `categoryBreakdown`
    - `waitlistedPatients` (`List<PatientSelectionDTO>`) — rank 41+:
      - `rank`, `patientId`, `patientName`, `score`, `daysOverdueSummary`
  - `PatientSelectionDTO`: rank, patientId, patientName, score, earliestTargetDate, slotDateTime (nullable), categoryBreakdown (list of `{type, weight, daysOverdue, bonus}`)
  - `ScoringService.getSelectionResult(Long weeklySelectionId)` → builds DTO from persisted entities.
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/SelectionResultDTO.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/PatientSelectionDTO.java`
  - Modificar: `src/main/java/com/priorizasus/priorizasus/service/ScoringService.java` (add `getSelectionResult`)
  - Create: `src/test/java/com/priorizasus/priorizasus/service/SelectionResultTest.java`
- **Tests**:
  - `SelectionResultTest`:
    - Build DTO from persisted WeeklySelection: verify rank order, scores, category breakdown
    - Verify waitlisted Patients have `slotDateTime = null`
    - Verify `totalEligible` = selected + waitlisted counts
    - All dates in BRT display format
- **Verification**:
  - [ ] DTO displays all required transparency fields
  - [ ] Category breakdown shows how Score was computed
  - [ ] Waitlisted Patients visible with accumulated daysOverdue
- **Depends on**: Task-SA-04 (executeWeeklySelection)

---

### Task-SA-06: Accumulated Fairness Tests

- **REQ**: SA-006 (Accumulated Fairness)
- **What**: Tests validating that waitlisted Patients gain priority over time.
  - **Key principle** (GRILL-DECISIONS #6):
    - Score NOT recalculated mid-week — only on the following Monday.
    - Each subsequent week, waitlisted Patients gain ~7 days of `daysOverdue` → +70 pts per Category.
    - Cap of 500 per Category prevents runaway scores.
  - Test scenarios:
    1. Patient has Score=400, rank 50 in Week 1
    2. In Week 2, same Patient has 7 more days overdue → Score=470, rank may improve
    3. In Week 3, Score=540, rank improves further
    4. Eventually cap at 500 bonus → Score stabilizes at weight + 500 (max per Category)
    5. Multi-category Patient gains +70 pts per Category per week (PRENATAL + CHRONIC → +140 pts/week total)
  - These are pure logic tests — no DB needed (mock the scorer, verify formula).
- **Where**:
  - Create: `src/test/java/com/priorizasus/priorizasus/service/AccumulatedFairnessTest.java`
- **Tests**:
  - `AccumulatedFairnessTest` (unit tests, mock `TargetDateCalculator`):
    - CHRONIC patient: 0 daysOverdue → bonus 0; 7 days → 70; 50 days → 500 (capped); 51 days → 500 (still capped)
    - PRENATAL patient at 36+w: weight always 1000; bonus increases weekly by 70 until cap
    - Patient at cap (bonus=500) for 3 consecutive weeks → Score unchanged (no further accumulation)
    - Verify a waitlisted Patient ranking 50 in Week 1 CAN reach top 40 by Week 5 (realistic scenario with 75 eligible)
- **Verification**:
  - [ ] Bonus capped at 500 per Category
  - [ ] Weekly accumulation: +70 pts/Category/week
  - [ ] Cap prevents runaway scores permanently blocking others
- **Depends on**: Task-SA-04 (executeWeeklySelection logic)

---

### Task-SA-07: ScoringAlgorithmController & Scheduled Trigger

- **REQ**: SA-004 (Weekly Selection trigger), SD-001 (staff can trigger manually)
- **What**: Create REST controller for Weekly Selection operations.
  - `ScoringAlgorithmController` (`@RestController`, `@RequestMapping("/api/scoring")`):
    - `POST /api/scoring/weekly-selection/run`:
      - Triggered by staff from dashboard (SD-001)
      - Calls `scoringService.executeWeeklySelection(currentWeekStart)`
      - Returns `SelectionResultDTO` with full ranking
      - On failure: returns error DTO with message
    - `GET /api/scoring/weekly-selection/latest`:
      - Returns `SelectionResultDTO` for the most recent Weekly Selection
    - `GET /api/scoring/weekly-selection/{weekStart}`:
      - Returns `SelectionResultDTO` for a specific Week
  - `@Scheduled` method for automatic Monday 7 AM trigger:
    - In `ScoringService` or a dedicated `@Component`
    - `@Scheduled(cron = "0 5 7 * * MON")` — 5 minutes after Slot creation (7:05 AM)
    - Ensures Slots are created first (CM-001 runs at 7:00 AM)
    - Calls `executeWeeklySelection(currentWeekStart)`
    - Logs result
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/controller/ScoringAlgorithmController.java`
  - Modificar: `src/main/java/com/priorizasus/priorizasus/service/ScoringService.java` (add `@Scheduled` method or delegate)
  - Create: `src/test/java/com/priorizasus/priorizasus/controller/ScoringAlgorithmControllerTest.java`
- **Tests**:
  - `ScoringAlgorithmControllerTest` (MockMvc):
    - `POST /api/scoring/weekly-selection/run` → 200, returns SelectionResultDTO
    - `POST /api/scoring/weekly-selection/run` when lock timeout → 409, error message
    - `GET /api/scoring/weekly-selection/latest` → 200, returns latest result or 404 if none
    - `GET /api/scoring/weekly-selection/2026-05-27` → 200, returns that week's result
- **Verification**:
  - [ ] Controller has NO business logic
  - [ ] Scheduled cron: `0 5 7 * * MON` (5 min after slot creation)
  - [ ] Manual trigger available for re-runs
  - [ ] Latest Selection queryable
- **Depends on**: Task-SA-04 (executeWeeklySelection), Task-SA-05 (SelectionResultDTO)

---

### Task-SA-08: Scoring Algorithm Integration Tests

- **REQ**: SA-001 through SA-006 (full Weekly Selection verification)
- **What**: End-to-end integration tests.
  1. `WeeklySelectionIntegrationTest` (`@SpringBootTest` + H2):
     - Create 80 Patients with various Categories → run Weekly Selection → verify:
       - 40 Patients have RESERVED Slots
       - 40 Patients waitlisted (Selection records with slot=null)
       - Top-ranked: PRENATAL 36+w patients first
       - Tie-breaking: earliest targetDate wins
       - Multi-category Patients scored correctly
     - Verify `WeeklySelection` record created with correct counts
     - Verify all 40 BATCH Slots transitioned to RESERVED
     - Verify weightBreakdown JSON stored per Selection
  2. Update `SpecConsistencyTest.REQUIRED_SPEC_FILES`.
- **Where**:
  - Create: `src/test/java/com/priorizasus/priorizasus/integration/WeeklySelectionIntegrationTest.java`
  - Modificar: `src/test/java/com/priorizasus/priorizasus/harness/SpecConsistencyTest.java`
- **Verification**:
  - [ ] Full Weekly Selection workflow passes
  - [ ] Correct ranking order (high-risk first)
  - [ ] 40 Slots RESERVED, rest waitlisted
  - [ ] Transaction rollback on error (no partial results)
- **Depends on**: Task-SA-01 through SA-07

---

## Cross-Cutting Concerns

Same as patient-master + capacity-model.
**Additional**:
- **Snapshot eligibility** (ADR-0005): Eligibility evaluated ONCE at Monday 7 AM. No mid-week re-evaluation.
- **Atomicity**: All-or-nothing transaction. Lock timeout → full rollback.
- **`daysOverdue` cap 500 per Category** (not total across Categories).
- **CHILD weight on NEXT milestone** — not current/last milestone.

---

**Status**: Ready for implementation
**Created**: May 24, 2026
