# Grill-with-Docs — Domain Decisions (PRIORIZASUS)

## Crystallized Decisions

### 1. Overdue Days Count ✅
- **Term:** `daysOverdue`
- **Definition:** Number of COMPLETE days between the target date and today, where counting starts on the day immediately AFTER the target date
- **Formula:** `daysOverdue = max(0, ChronoUnit.DAYS.between(targetDate, today))`
- **Examples:**
  - Target: 01-jun, Today: 01-jun → **0 days overdue** (still on time)
  - Target: 01-jun, Today: 02-jun → **1 day overdue**
  - Target: 01-jun, Today: 05-jun → **4 days overdue**
- **Bonus applied:** `bonus = min(daysOverdue × 10, 500)` (capped at 500)
- **Status:** ✅ APPROVED

### 2. Slot Status Lifecycle ✅
- **States:** AVAILABLE → RESERVED → BOOKED (and CANCELLED at any point)
- **AVAILABLE:**
  - No patient reserved
  - Any eligible patient can book (batch selection)
  - Slot is "open for claiming"
- **RESERVED:**
  - Batch allocated to specific patient X
  - `patient_id = X` is set on the Slot
  - Only X can confirm booking until Friday
  - Represents a promise: "This slot is yours if you want it"
  - **Lock during batch:** Pessimistic lock (DB level, `FOR UPDATE NOWAIT`) prevents double-booking during execution
- **BOOKED:**
  - Patient X confirmed via POST /api/booking/reserve
  - Appointment created with status CONFIRMED
  - Slot now "locked" (appointment exists)
- **CANCELLED:**
  - Patient cancels appointment
  - Slot reverts to AVAILABLE (released for other patients)
  - Timestamp + reason logged
- **Status:** ✅ APPROVED

### 3. Unconfirmed RESERVED Slots (End-of-Week Handling) ✅
- **Trigger:** Automatic job Friday 5 PM
- **Behavior:**
  - Any slot with status `RESERVED` that was NOT confirmed (status ≠ BOOKED)
  - Transition: `RESERVED` → `EXPIRED`
  - These slots are **lost** (not reused this week or next)
- **Rationale:** Simplistic MVP; acceptable for Phase 1
- **Occupancy impact:** If 5 of 40 slots remain RESERVED but unbooked → occupancy drops (e.g., 30/40 = 75%)
- **Phase 2+:** Implement "auto-release" or "carryover for next week" if occupancy < threshold
- **Status:** ✅ APPROVED

### 4. Semantic Difference: lastConsultationDate vs targetDate ✅
- **`lastConsultationDate`:**
  - FACTUAL date of the last completed consultation
  - Updated every time the patient has a consultation (appointment marked COMPLETED)
  - Historical field, not a deadline
  - Never "overdue"
- **`targetDate` (clinicalWindowTargetDate):**
  - Date of the NEXT mandatory consultation (clinical deadline)
  - Automatically recalculated after each consultation: `targetDate = lastConsultationDate + ruleInterval`
  - **Rule per category:**
    - PRENATAL <28w: +30 days
    - PRENATAL 28-36w: +15 days
    - PRENATAL 36+w: +7 days
    - CHILD (milestones): per table (day 7, 30, 2m, 4m, 6m, 9m, 12m, 18m, 24m, annual)
    - CHRONIC: +60 days
- **Scoring:** uses `targetDate`
  - `daysOverdue = ChronoUnit.DAYS.between(targetDate, today)`
- **Eligibility (Scoring REQ-001):** filters by `lastConsultationDate`
  - Blocks: `(today - lastConsultationDate).days < 7`
  - Reason: Respect minimum interval between consultations, avoid "excessive weekly consultations"
- **Example:**
  - Maria has consultation 01-jun (PRENATAL 24w)
  - `lastConsultationDate` = 01-jun
  - `targetDate` = 01-jul (30 days later)
  - If she attends again 02-jun (extra):
    - `lastConsultationDate` → 02-jun ✓
    - `targetDate` → 02-jul ✓ (recalculates)
- **Status:** ✅ APPROVED

### 5. Scoring with Multiple Categories ✅
- **Rule:** `Score = SUM(weight_i) + SUM(bonus_i)` for all active categories
- **Calculation per category:**
  - Each category has its own `targetDate` (derived from its clinical rules)
  - `daysOverdue_i = ChronoUnit.DAYS.between(targetDate_i, today)`
  - `bonus_i = min(daysOverdue_i × 10, 500)` (capped per category)
- **Aggregation:**
  - Sum weights: `totalWeight = sum(weight_PRENATAL, weight_CHRONIC, ...)`
  - Sum bonuses: `totalBonus = sum(bonus_PRENATAL, bonus_CHRONIC, ...)`
  - Final score: `totalScore = totalWeight + totalBonus`
- **Example: Maria (PRENATAL 36+ weeks + CHRONIC)**
  ```
  Prenatal:  weight=1000, daysOverdue=10 → bonus=100
  Chronic:   weight=200,  daysOverdue=20 → bonus=200
  ─────────────────────────────────────────────
  Score = (1000+200) + (100+200) = 1500
  ```
- **Impact:** Patients with multiple clinical needs have proportional priority ✓
- **Rationale:** Fair (both needs are real and independent; sum reflects multiple urgencies)
- **Status:** ✅ APPROVED

### 6. Overdue Days Accumulation (Long-Term Fairness) ✅
- **Mid-week behavior:**
  - `targetDate` remains FIXED from Monday batch through Sunday
  - `daysOverdue` increases each day (e.g., 20 days Mon → 24 days Fri)
  - Score is NOT recalculated mid-week
- **Weekly batch:**
  - Every **Monday 7 AM**, batch runs again
  - Recalculates scores for ALL patients with updated `daysOverdue`
  - Examples:
    - John (Week 1): 20 days overdue → Score 400 → rank 50 (not selected)
    - John (Week 2): 27 days overdue → Score 470 → rank 45 (rises, but still not selected)
    - John (Week 3): 34 days overdue → Score 540 → rank 30 (finally selected?)
- **Accumulative fairness:**
  - Waitlisted patients gain +7 days bonus each week
  - Score rises incrementally (10 pts/day × 7 days = +70 pts/week)
  - Guardrail: Bonus capped at 500 (prevents "super-old" patients from dominating)
  - Nobody is "locked out" permanently (always a chance next week)
- **Overlaps & conflicts:**
  - If John is selected Week 2 but already has a BOOKED appointment Week 1 (carryover):
    - Week 2 batch ignores him (already has 1 active appointment, eligibility blocks)
    - John only becomes eligible again when Week 1 appointment completes or cancels
- **Status:** ✅ APPROVED

### 7. Interaction: "1-Appointment-Per-Week" vs Batch Selection ✅
- **Clear rule:** "1 BOOKED appointment per week per patient" (RESERVED does not count)
- **Rationale:**
  - RESERVED = allocation by batch (promise, not yet committed)
  - BOOKED = patient confirmed (real appointment, consuming resource)
  - Counting RESERVED as "appointment" would confuse UX and block flexibility
- **Possible simultaneous states:**
  - John may have: 1 RESERVED (Tuesday, from batch) + 0 BOOKED → can book another available BATCH
  - John may have: 1 RESERVED (Tuesday) + 1 BOOKED (Thursday) → max 2 in transit
  - John CANNOT have: 2 BOOKED (one will be rejected with "Already have 1 BOOKED this week")
- **Booking decision flow:**
  ```
  POST /api/booking/reserve {patientId, slotId}
  ├─ Check: patient has BOOKED appointment this week?
  │  ├─ YES → HTTP 400: "You already have 1 appointment booked; max 1 per week"
  │  └─ NO → Proceed
  ├─ Check: slot type = BATCH?
  │  ├─ YES → Patient must be in current batch selection (status RESERVED)
  │  │        If not → HTTP 403: "You are not eligible for this slot"
  │  └─ NO → (not applicable in Phase 1; all slots are BATCH)
  └─ Acquire pessimistic lock, create Appointment
  ```
- **Edge case: John changes his mind**
  - Has RESERVED Tuesday + BOOKED Thursday
  - Cancels Thursday BOOKED → reverts to AVAILABLE
  - Can then book another Friday BATCH (different week slot if exists)
- **Status:** ✅ APPROVED

### 8. "Week" Definition ✅
- **Definition:** Week = "Slot Week" (batch week, Monday–Friday)
- **Identifier:** `week_start` = date of the Monday that starts the week
- **Examples:**
  - Week 1: week_start = 27-May-2026, slots from 27-May to 31-May
  - Week 2: week_start = 03-June-2026, slots from 03-June to 07-June
- **Appointments belong to the week:** The week in which the slot was created/exists
  - John booking Tuesday 28-May (slot with week_start=27-May) → belongs to Week 1
  - John booking Monday 03-June (slot with week_start=03-June) → belongs to Week 2
- **"1 per week" validation:**
  ```sql
  SELECT COUNT(*) FROM Appointment
  WHERE patient_id = :patientId
    AND slot.week_start = :week_start
    AND status = 'BOOKED'
  ```
  If COUNT >= 1 → blocked
- **Batch eligibility check:**
  - "Not already booked in current week" = `WHERE week_start = CURRENT_BATCH_WEEK_START AND status = BOOKED`
  - Recalculated every Monday when new batch runs
- **Status:** ✅ APPROVED

### 9. Eligibility: Snapshot vs Continuous ✅
- **Model:** "Snapshot at batch" (Monday 7 AM)
- **Rationale:**
  - Fairness: Selection rules are deterministic and don't change mid-week
  - UX: John sees RESERVED slot as guaranteed, no risk of revocation
  - Auditability: "Why was John selected?" → answer is reproducible
- **Behavior:**
  - Batch runs Monday 7 AM with eligibility snapshot from that moment
  - Mid-week status changes (e.g., John has consultation Wednesday) do NOT affect selection
  - John keeps his RESERVED + can book
  - Appointment BOOKED on Friday remains valid, even if John is now "ineligible"
- **Next batch:**
  - Following Monday (3-June), John is re-evaluated with updated data
  - If now ineligible: excluded from Week 2 batch
  - If eligible again: included in new batch (score recalculated)
- **Edge case: Cancellation by status change**
  - Not implemented in Phase 1
  - Phase 2+: May add "pre-appointment eligibility check" (e.g., if patient status = INACTIVE, auto-cancel)
- **Status:** ✅ APPROVED

### 10. Puericulture Milestones (CHILD Category) ✅
- **Ministry of Health Milestones:**
  - Days: 7, 30
  - Months: 2, 4, 6, 9, 12, 18, 24
  - Annual: Birthday
- **Data model:**
  - `Patient.birthDate` (fixed, immutable)
  - `Patient.lastMilestoneCompleted` (enum: DAY_7, DAY_30, M2, M4, M6, M9, M12, M18, M24, ANNUAL, etc.)
  - `Patient.targetDate` = automatically calculated based on (birthDate + next milestone)
- **targetDate calculation:**
  ```
  nextMilestoneEnum = getNextMilestone(lastMilestoneCompleted)
  targetDate = birthDate + daysToMilestone(nextMilestoneEnum)
  
  Examples (birth = 01-June-2026):
    lastMilestoneCompleted = null (newborn)
      → nextMilestoneEnum = DAY_7
      → targetDate = 01-June + 7 = 08-June
    
    lastMilestoneCompleted = DAY_7
      → nextMilestoneEnum = DAY_30
      → targetDate = 01-June + 30 = 01-July
    
    lastMilestoneCompleted = DAY_30
      → nextMilestoneEnum = MONTH_2
      → targetDate = 01-June + 60 = 31-July (approx)
  ```
- **Weight by age range (derived from next milestone):**
  - Days 7, 30: weight = 900 (newborn, critical window)
  - Months 2, 4, 6, 9, 12: weight = 700 (puericulture active)
  - Months 18, 24 + Annual: weight = 400 (development checks)
- **Update after completed appointment:**
  ```
  markAppointmentCompleted(appointment):
    patient = appointment.patient
    patient.lastConsultationDate = today
    patient.lastMilestoneCompleted = (nextMilestoneEnum computed in batch)
    patient.targetDate = calculateNextMilestone(...)
    save(patient)
  ```
- **Status:** ✅ APPROVED

### 11. Gestational Weeks (PRENATAL Category Dynamics) ✅
- **Data model:**
  - `Patient.ultrasoundDate` (date of last ultrasound)
  - `Patient.gestationalWeeksAtUltrasound` (weeks confirmed on ultrasound)
  - `Patient.calculateCurrentGestationalWeeks()` → computed method:
    ```
    days_since = today - ultrasoundDate
    weeks_elapsed = days_since / 7.0  (fractional weeks)
    current_weeks = gestationalWeeksAtUltrasound + weeks_elapsed
    ```
- **Weight recalculation (dynamic, every batch):**
  ```
  currentWeeks = patient.calculateCurrentGestationalWeeks()
  if currentWeeks < 28:       weight = 300
  elif currentWeeks < 36:     weight = 500
  else (36+):                 weight = 1000
  ```
  - Example: Maria 27.9w (Monday) → weight 300
    Next week 28.9w (Monday) → weight 500 (rises!)
- **Target date recalculation (dynamic, every batch or daily):**
  ```
  if currentWeeks < 28:       targetDate = today + 30 days
  elif currentWeeks < 36:     targetDate = today + 15 days
  else:                       targetDate = today + 7 days
  ```
- **Impact:** Maria who was rank 60 (27w, weight 300) may rise to rank 40 (28w, weight 500) in the next batch
- **Fairness:** Advanced pregnancies gain progressive priority (makes clinical sense)
- **Status:** ✅ APPROVED

### 12. Lock Recovery & Crash Handling ✅
- **Strategy:** Pessimistic lock + timeout auto-release + alerting
- **Lock timeout configuration:**
  ```sql
  SET lock_timeout = '30s';
  BEGIN TRANSACTION;
  SELECT * FROM Slot 
    WHERE type='BATCH' AND status IN ('AVAILABLE', 'RESERVED')
    FOR UPDATE NOWAIT;
  -- If lock not acquired within 30s: auto-rollback, transaction terminated
  ```
- **Failure scenarios:**
  - Batch crashes mid-execution → locks released after 30s (PostgreSQL connection timeout)
  - Another batch runs simultaneously → 2nd batch gets timeout → fails with clear error
  - Staff can retry immediately after error
- **Audit logging:**
  ```sql
  INSERT INTO WeeklyBatchLog (week_start, status, error_message, timestamp)
  VALUES (?, 'FAILED', 'Lock timeout or crash detected', now())
  ```
- **Monitoring (Phase 1):**
  - Staff dashboard shows: "Last batch: SUCCESS (40 selected) on Mon 27-May 7:05 AM"
  - If failure: "FAILED: Lock timeout (try again)"
- **Phase 2+ improvements:**
  - Dry-run: "What would batch do?" (read-only, no locks)
  - Idempotency: Re-running same batch doesn't double-reserve
  - Automated recovery job that cleans stale locks
- **Status:** ✅ APPROVED

### 13. Slot Visibility for Non-Selected Patients ✅
- **Strategy:** Labeled Visibility (transparency + clear UX)
- **API response:**
  ```json
  {
    "batchSlots": [
      {
        "id": 1,
        "dateTime": "2026-05-28T09:00:00",
        "status": "RESERVED",
        "visibility": "RESERVED_FOR_ANOTHER",
        "message": "This slot is reserved for another patient."
      }
    ]
  }
  ```
- **UI/UX for John (non-selected):**
  - "40 slots reserved for selected patients"
  - BATCH Slots shown with 🔒 "Reserved" (non-clickable)
- **Transparency:** John sees that 40 patients were selected (visible fairness)
- **Benefit:** Encourages John to wait for next batch with accumulated score
- **Status:** ✅ APPROVED

### 14. Marking Appointment as COMPLETED ✅
- **Actor:** Staff (nurse/receptionist/admin) marks appointment after patient consultation
- **Endpoint:** `POST /api/staff/appointments/{appointmentId}/complete`
- **Request payload:**
  ```json
  {
    "statusAtArrival": "PRESENT" | "NO_SHOW" | "RESCHEDULED",
    "completedAt": "2026-05-28T09:30:00",
    "notesFromConsultation": "Patient discussed X, prescribed Y..."
  }
  ```
- **On successful completion (statusAtArrival = "PRESENT"):**
  - `appointment.status = COMPLETED`
  - `appointment.completedAt = now()`
  - `patient.lastConsultationDate = today`
  - `patient.targetDate = recalculateTargetDate(patient.categories)` (triggers next milestone/window)
  - Audit: Log staff user, timestamp, status
- **Other statuses (Phase 2+):**
  - NO_SHOW: `appointment.status = NO_SHOW` (doesn't update lastConsultationDate; stays overdue)
  - RESCHEDULED: Different flow (appointment cancels, patient can re-book)
- **Audit logging:**
  ```sql
  INSERT INTO AppointmentLog 
    (appointment_id, staff_user, status, timestamp, notes)
  VALUES (...)
  ```
- **Rationale:** Staff has operational knowledge ("patient actually came"); audit provides accountability
- **Status:** ✅ APPROVED

### 15. Timezone Handling (UTC Storage + Local Display) ✅
- **Storage:** All timestamps in UTC (ISO-8601 with Z suffix)
  ```sql
  appointment.appointmentDateTime = '2026-05-28T12:00:00Z'  (9 AM BRT)
  batch.executedAt = '2026-05-27T10:00:00Z'  (7 AM BRT)
  patient.lastConsultationDate = '2026-05-26T14:30:00Z'
  ```
- **Database config:**
  ```properties
  spring.jpa.properties.hibernate.jdbc.time_zone=UTC
  ```
- **Application:** LocalDateTime + ZoneId for context
  ```java
  ZoneId clinicZone = ZoneId.of("America/Sao_Paulo");
  LocalDateTime displayTime = 
    appointmentDateTime
      .atZone(ZoneId.of("UTC"))
      .withZoneSameInstant(clinicZone)
      .toLocalDateTime();
  // Display: "28-May 09:00" (local clinic time)
  ```
- **Configuration:**
  ```properties
  clinic.timezone=America/Sao_Paulo
  ```
- **Duration calculations:** Use LocalDate (ignores DST/timezone)
  ```java
  long daysOverdue = ChronoUnit.DAYS.between(
    targetDate, 
    LocalDate.now(ZoneId.of("America/Sao_Paulo"))
  );
  ```
- **Rationale:**
  - UTC in DB: Portable, unambiguous
  - Local display: Clinic sees correct times
  - Duration: LocalDate avoids DST issues (7 days is always 7 days)
- **Status:** ✅ APPROVED

### 16. Elimination of WALK_IN — 40 All-BATCH ✅
- **Decision:** All 40 weekly slots are BATCH, allocated exclusively by the scoring algorithm
- **Rationale:**
  - Simplicity: A single allocation path, no distinction between Slot types
  - Maximum fairness: Every Slot is filled by clinical priority, not by arrival order
  - Canonical rule: `Business Rules` establishes "40 patients with highest score"
- **Impact on docs:**
  - ADR-0002 rewritten (was 35/5, now 40/0)
  - CONTEXT.md: WALK_IN Slot definition removed
  - Specs for capacity-model, booking-system, scoring-algorithm: all adjusted to 40 BATCH
  - Term WALK_IN eliminated from canonical glossary
- **Phase 2:** Spontaneous (acute) demand may be treated as a separate scoring category or distinct mechanism, if occupancy data shows need
- **Status:** ✅ APPROVED

---

## Cross-References (post grill-with-docs, May 23, 2026)

- **CONTEXT.md** — Canonical glossary of all domain terms. See root `CONTEXT.md`.
- **ADR-0001** — Pessimistic Locking with NOWAIT → `docs/adr/0001-pessimistic-locking-nowait.md`
- **ADR-0002** — 40/0 All-BATCH (No WALK_IN Split) → `docs/adr/0002-slot-capacity-split.md`
- **ADR-0003** — UTC Storage, Local Display → `docs/adr/0003-utc-storage-local-display.md`
- **ADR-0004** — Phase 1 Scope Boundary → `docs/adr/0004-phase1-scope-boundary.md`
- **ADR-0005** — Snapshot Eligibility Model → `docs/adr/0005-snapshot-eligibility-model.md`

### Term Changes from grill-with-docs

| Old Term (specs) | New Canonical Term | Rationale |
|-----------------|-------------------|-----------|
| SCHEDULED slot | **BATCH Slot** | Describes allocation method, not status |
| ~~SPONTANEOUS slot~~ | ~~WALK_IN Slot~~ | **Eliminated** — Decision #16: all 40 slots are BATCH |
| Clinical Window | **`targetDate`** + **`lastConsultationDate`** | Split fuzzy term into two precise dates (already in #4) |
| Batch (process) | **Weekly Selection** | Disambiguate from BATCH Slot |
| Booking (noun) | **Appointment** | Booking is the verb; Appointment is the confirmed result |
| Override (specific) | **Reassign** / **Release** | Two distinct staff actions |

## Open Questions
(to be resolved)
