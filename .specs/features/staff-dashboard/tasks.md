# Staff Dashboard — Implementation Tasks

> **Feature**: Staff Dashboard — Weekly Selection control, occupancy monitoring, patient management, audit & reporting
> **Spec**: `.specs/features/staff-dashboard/spec.md`
> **Design**: No separate design.md — tasks are detailed to compensate
> **Dependencies**: ALL Phase 1 features (patient-master, capacity-model, scoring-algorithm, booking-system)
> **Depended on by**: None (terminal feature in Phase 1)
> **Canonical terms**: CONTEXT.md — Override, Reassign, Release, Suspend, Occupancy, Audit Trail, No-Show

---

## Task Dependency Graph

```
Task-SD-01 (StaffController — Weekly Selection control)      ← Depends on SA-04, SA-07, CM-05
Task-SD-02 (OccupancyService — metrics + color-coded)        ← Depends on CM-05, BK-02
Task-SD-03 (StaffController — mark COMPLETED)                ← Depends on BK-02, PM-09
Task-SD-04 (StaffController — patient management + Suspend)  ← Depends on PM-05, PM-08
Task-SD-05 (AuditLogService — query + CSV export)            ← Depends on PM-01 (AuditLog), CM-07
Task-SD-06 (SystemHealthService — health indicators)         ← Depends on all (queries everything)
Task-SD-07 (Thymeleaf templates — dashboard + patient)       ← Depends on SD-01 through SD-04
Task-SD-08 (Integration tests)                               ← Depends on SD-01 through SD-07
```

---

## Tasks

### Task-SD-01: StaffController — Weekly Selection Control, Reassign & Release

- **REQ**: SD-001 (Weekly Selection Control Panel)
- **What**: Create `StaffController` (`@Controller` for Thymeleaf SSR + `@RestController` endpoints for actions).
  - `GET /staff/dashboard` → renders main dashboard page (`staff/dashboard.html`)
  - `POST /api/staff/weekly-selection/run`:
    - Triggers `scoringService.executeWeeklySelection(currentWeekStart)`
    - Returns result or error message
  - `GET /api/staff/weekly-selection/current`:
    - Returns `SelectionResultDTO` for the current Week
    - Includes full Ranking table: selected (rank 1–40) and non-selected (rank 41+)
  - `POST /api/staff/reassign`:
    - Request body: `{ patientId, currentSlotId, newSlotId, reason }`
    - Validates: new Slot is AVAILABLE, Patient is ACTIVE
    - Releases current Slot (via `capacityService.transitionSlot(currentSlot, CANCELLED, ...)`)
    - Reserves new Slot (via `capacityService.transitionSlot(newSlot, RESERVED, patientId, ...)`)
    - Updates Selection record with new Slot reference
    - Logs audit: `REASSIGN` action type with old/new Slot IDs and reason
    - Returns confirmation
  - `POST /api/staff/release`:
    - Request body: `{ appointmentId, reason }`
    - Finds Appointment → validates status is CONFIRMED
    - Calls `capacityService.transitionSlot(slot, CANCELLED, ...)` → Slot reverts to AVAILABLE
    - Appointment status → `CANCELLED`
    - Selection status → `RELEASED`
    - Logs audit: `RELEASE` action type (distinct from `CANCELLATION` — staff-initiated)
    - Returns confirmation
  - **Important**: Reassign and Release are STAFF actions — distinct from Patient cancellation (`CANCELLATION`). Different audit log action types.
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/controller/StaffController.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/ReassignRequestDTO.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/ReleaseRequestDTO.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/controller/StaffControllerTest.java`
- **Tests**:
  - `StaffControllerTest` (MockMvc):
    - `GET /staff/dashboard` → 200, dashboard template rendered
    - `POST /api/staff/weekly-selection/run` → 200, Weekly Selection executed
    - `POST /api/staff/weekly-selection/run` when already run → re-runs (releases previous)
    - `POST /api/staff/reassign` valid → 200, Slot moved, audit logged
    - `POST /api/staff/reassign` newSlot already BOOKED → 400
    - `POST /api/staff/release` valid → 200, Appointment cancelled, Slot freed
    - `POST /api/staff/release` appointment already COMPLETED → 400
- **Verification**:
  - [ ] Weekly Selection trigger delegates to `ScoringService`
  - [ ] Reassign: old Slot freed, new Slot reserved, Selection updated
  - [ ] Release: distinct from Patient cancellation (audit log differs)
  - [ ] All staff actions logged with staff user (hardcoded "system" for Phase 1)
  - [ ] Controller has NO `@Transactional` — delegates to services
- **Depends on**: Task-SA-04 (executeWeeklySelection), Task-SA-05 (SelectionResultDTO), Task-CM-05 (transitionSlot), Task-BK-04 (Appointment records)

---

### Task-SD-02: OccupancyService — Metrics & Color-Coded Alerts

- **REQ**: SD-002 (Occupancy Dashboard)
- **What**: Create `OccupancyService` calculating occupancy metrics per Week.
  1. `calculateOccupancy(LocalDate weekStart)`:
     - Queries Slots for the Week via `SlotRepository`
     - Counts:
       - `totalSlots`: 40 (or actual created Slots if holiday-shortened)
       - `bookedSlots`: count where `status == BOOKED`
       - `cancelledSlots`: count where `status == CANCELLED`
       - `expiredSlots`: count where `status == EXPIRED`
       - `utilizationPercent`: `(bookedSlots / totalSlots) × 100`
     - Returns `OccupancyDTO`
  2. `calculateOccupancyByCategory(LocalDate weekStart)`:
     - For each Category type (PRENATAL, CHILD, CHRONIC):
       - `eligibleCount`: Patients with that active Category who were eligible at Weekly Selection time
       - `bookedCount`: Appointments where Patient has that Category AND Appointment status is CONFIRMED/COMPLETED
       - `coveragePercent`: `(bookedCount / eligibleCount) × 100`
     - Returns `Map<CategoryType, CategoryOccupancyDTO>`
  3. `getAlertLevel(double coveragePercent)`:
     - 🟢 Green: ≥80% — "Well-covered"
     - 🟡 Yellow: 60–79% — "At risk"
     - 🔴 Red: <60% — "Critical"
  4. `getHistoricalOccupancy(int weeksBack)`:
     - Returns occupancy data for the last N Weeks for trend chart
  - **Endpoint**: `GET /api/staff/occupancy?weekStart={date}` returns full occupancy data.
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/service/OccupancyService.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/CategoryOccupancyDTO.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/service/OccupancyServiceTest.java`
- **Tests**:
  - `OccupancyServiceTest`:
    - 34 BOOKED, 3 CANCELLED, 3 EXPIRED → 85% utilization
    - 40 BOOKED → 100% utilization (full week)
    - 10 BOOKED, 30 AVAILABLE → 25% utilization 🔴
    - PRENATAL: 8 eligible, 8 booked → 100% 🟢
    - CHILD: 12 eligible, 8 booked → 67% 🟡
    - CHRONIC: 60 eligible, 23 booked → 38% 🔴
    - Holiday week: 32 Slots created, 30 booked → 93.75%
    - `getAlertLevel(100)` → GREEN; `getAlertLevel(65)` → YELLOW; `getAlertLevel(30)` → RED
- **Verification**:
  - [ ] Occupancy formula: `(BOOKED / totalSlots) × 100`
  - [ ] Category coverage formula: `(booked / eligible) × 100`
  - [ ] Alert thresholds correct: ≥80, 60–79, <60
  - [ ] Handles 0 eligible gracefully (division by zero → 0%)
- **Depends on**: Task-CM-05 (Slot queries), Task-BK-02 (Appointment queries), Task-PM-04 (Category queries)

---

### Task-SD-03: StaffController — Mark Appointment as COMPLETED

- **REQ**: SD-003 (Mark Appointment as COMPLETED), GRILL-DECISIONS #14
- **What**: Add endpoint for staff to mark Appointments after consultation.
  - `POST /api/staff/appointments/{appointmentId}/complete`:
    - Request body:
      ```json
      {
        "statusAtArrival": "PRESENT",
        "completedAt": "2026-05-28T12:30:00Z",
        "notesFromConsultation": "Routine checkup, all normal"
      }
      ```
    - Validates Appointment exists and status is `CONFIRMED`
    - If `statusAtArrival == "PRESENT"`:
      - Appointment status → `COMPLETED`
      - `appointment.completedAt = request.completedAt`
      - `appointment.notesFromConsultation = request.notesFromConsultation`
      - Calls `patientService.updateLastConsultation(patientId, today)` → updates `lastConsultationDate` and recalculates `targetDate` (PM-005)
    - If `statusAtArrival == "NO_SHOW"` (Phase 2 stub):
      - For Phase 1: logs warning, sets status to `NO_SHOW`, does NOT update `lastConsultationDate`
    - If `statusAtArrival == "RESCHEDULED"` (Phase 2 stub):
      - For Phase 1: cancels current Appointment, Patient can re-book
    - Logs audit: `COMPLETED` action type
    - Returns updated Appointment DTO
  - **Request DTO**: `CompleteAppointmentRequestDTO`:
    - `statusAtArrival` (enum: `PRESENT`, `NO_SHOW`, `RESCHEDULED`)
    - `completedAt` (Instant)
    - `notesFromConsultation` (String, optional)
  - **No-show flagging** (Phase 1 stub):
    - `GET /api/staff/appointments/past-unmarked` → finds CONFIRMED Appointments with `slotDateTime` in past
    - Staff reviews and marks as PRESENT/NO_SHOW
- **Where**:
  - Modificar: `src/main/java/com/priorizasus/priorizasus/controller/StaffController.java` (add endpoint)
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/CompleteAppointmentRequestDTO.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/controller/StaffCompleteAppointmentTest.java`
- **Tests**:
  - `StaffCompleteAppointmentTest` (MockMvc):
    - `POST /api/staff/appointments/1/complete` PRESENT → 200, Appointment COMPLETED, `lastConsultationDate` updated
    - `POST /api/staff/appointments/1/complete` NO_SHOW → 200, Appointment NO_SHOW, `lastConsultationDate` NOT updated
    - `POST /api/staff/appointments/1/complete` for already COMPLETED → 400
    - `POST /api/staff/appointments/1/complete` for CANCELLED → 400
    - Verify `targetDate` recalculated after COMPLETED (PRENATAL weight may change)
    - Audit log entry created
- **Verification**:
  - [ ] `lastConsultationDate` updated on PRESENT
  - [ ] `targetDate` recalculated per Category rules
  - [ ] `NO_SHOW` does NOT update `lastConsultationDate`
  - [ ] Cannot complete already-completed appointment
  - [ ] Past-unmarked query works for no-show review
- **Depends on**: Task-BK-02 (AppointmentRepository), Task-PM-09 (updateLastConsultation)

---

### Task-SD-04: StaffController — Patient Management & Suspend

- **REQ**: SD-004 (Patient Management)
- **What**: Add patient management endpoints to `StaffController`.
  - `GET /staff/patients`:
    - Thymeleaf-rendered patient list page
    - Filters: Category type, PatientStatus, overdue status (on-time, overdue <7d, overdue >7d)
    - Search: name, CPF
  - `GET /staff/patients/{id}`:
    - Patient detail view: all Appointments (past + future), current Categories + `targetDate`, contact info
    - Action buttons: "Reassign", "Release", "Suspend Patient"
  - `POST /api/staff/patients/{id}/suspend`:
    - Request body: `{ reason }`
    - Calls `patientService.suspendPatient(id, reason)`
    - Returns confirmation
  - `GET /api/staff/patients?status=ACTIVE&category=PRENATAL&overdue=true`:
    - REST endpoint for filtered patient queries
    - Returns `List<PatientViewDTO>`
  - **Cancellation log**: `GET /api/staff/cancellations?weekStart={date}` → list of all cancellations (Patient + Staff) for the Week.
    - Cancellation rate: `(cancelledAppointments / totalBookedAppointments) × 100`
- **Where**:
  - Modificar: `src/main/java/com/priorizasus/priorizasus/controller/StaffController.java` (add endpoints)
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/PatientFilterDTO.java`
  - Create: `src/test/java/com/priorizasus/priorizasus/controller/StaffPatientManagementTest.java`
- **Tests**:
  - `StaffPatientManagementTest` (MockMvc):
    - `GET /api/staff/patients?status=ACTIVE` → 200, filtered list
    - `GET /api/staff/patients?category=PRENATAL` → 200, only PRENATAL patients
    - `GET /api/staff/patients?overdue=true` → 200, only overdue patients
    - `GET /api/staff/patients/search?cpf=12345678901` → 200, single patient
    - `POST /api/staff/patients/1/suspend` → 200, status SUSPENDED, audit logged
    - `POST /api/staff/patients/1/suspend` already SUSPENDED → 400
    - `GET /api/staff/cancellations?weekStart=2026-05-27` → 200, list of cancellations
- **Verification**:
  - [ ] Patient list filterable by status, category, overdue
  - [ ] Search by CPF and name works
  - [ ] Suspend transitions ACTIVE → SUSPENDED
  - [ ] Cancellation log shows both Patient and Staff cancellations
- **Depends on**: Task-PM-05 (PatientService), Task-PM-08 (status lifecycle), Task-BK-05 (cancellation records)

---

### Task-SD-05: AuditLogService — Query & CSV Export

- **REQ**: SD-005 (Audit Trail & Reporting)
- **What**: Create `AuditLogService` for audit trail querying and CSV export.
  1. `queryAuditLog(AuditLogFilterDTO filter)`:
     - Filters: date range (from/to Instant), action type (AuditActionType), patientId
     - Returns paginated list of `AuditLogEntryDTO`
  2. `exportAuditCsv(AuditLogFilterDTO filter, OutputStream out)`:
     - Generates CSV with headers: Timestamp (BRT), Action Type, Staff User, Patient ID, Slot ID, Appointment ID, Details
     - Uses `ClinicTimeZone` for timestamp display
     - Writes to `OutputStream` (for HTTP response streaming)
  3. Report types:
     - **Occupancy Report**: Weekly occupancy + by-Category metrics → CSV
     - **Category Coverage Report**: % eligible booked per Category per Week → CSV
     - **Cancellation Report**: Cancellation rate, reasons breakdown → CSV
  4. `POST /api/staff/reports/occupancy?weekStart={date}&format=csv`:
     - Returns CSV file download
  5. `POST /api/staff/reports/category-coverage?weekStart={date}&format=csv`:
     - Returns CSV file download
  6. `GET /api/staff/audit-log?from={}&to={}&actionType={}&patientId={}`:
     - Returns paginated audit log entries
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/service/AuditLogService.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/AuditLogFilterDTO.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/AuditLogEntryDTO.java`
  - Modificar: `src/main/java/com/priorizasus/priorizasus/controller/StaffController.java` (add report endpoints)
  - Create: `src/test/java/com/priorizasus/priorizasus/service/AuditLogServiceTest.java`
- **Tests**:
  - `AuditLogServiceTest`:
    - Query by date range returns correct entries
    - Query by action type (BOOKING) returns only BOOKING entries
    - Query by patientId returns only that Patient's entries
    - CSV export: verify headers, BRT timestamps, correct row count
    - Empty result → CSV with headers but 0 data rows
    - Occupancy report CSV: correct counts per Week
    - Category coverage CSV: correct % per Category
- **Verification**:
  - [ ] Audit log queryable by date range, action type, patient
  - [ ] CSV export produces valid CSV format
  - [ ] Timestamps in BRT in CSV output
  - [ ] Reports generate with correct metrics
- **Depends on**: Task-PM-01 (AuditLog entity + repository), Task-CM-07 (SlotAuditLog), Task-SD-02 (OccupancyService)

---

### Task-SD-06: SystemHealthService — Health Indicators

- **REQ**: SD-006 (System Health)
- **What**: Create health check endpoints.
  - `GET /api/staff/health`:
    - Returns:
      ```json
      {
        "databaseConnection": "UP" | "DOWN",
        "lastWeeklySelection": {
          "weekStart": "2026-05-27",
          "executedAt": "2026-05-27T10:05:00Z",
          "status": "COMPLETED",
          "totalSelected": 40
        },
        "scheduledTasksEnabled": true,
        "recentErrors": [
          { "timestamp": "...", "error": "Lock timeout", "resolved": false }
        ]
      }
      ```
  - `GET /api/staff/health/recent-errors`:
    - Returns last 10 `WeeklySelection` records with `status = FAILED` or recent error conditions
    - Useful for debugging lock timeouts, transaction failures
- **Where**:
  - Create: `src/main/java/com/priorizasus/priorizasus/service/SystemHealthService.java`
  - Create: `src/main/java/com/priorizasus/priorizasus/dto/SystemHealthDTO.java`
  - Modificar: `src/main/java/com/priorizasus/priorizasus/controller/StaffController.java` (add health endpoints)
  - Create: `src/test/java/com/priorizasus/priorizasus/service/SystemHealthServiceTest.java`
- **Tests**:
  - `SystemHealthServiceTest`:
    - DB connection: mock → UP
    - Last Weekly Selection: query repository → returns latest
    - No Weekly Selection yet → `lastWeeklySelection: null`
    - Recent errors: query FAILED selections → returns list
    - Scheduled tasks enabled: reads config → true/false
- **Verification**:
  - [ ] Health endpoint returns all required fields
  - [ ] Database connection status accurate
  - [ ] Last Weekly Selection details returned
  - [ ] Error log helpful for debugging
- **Depends on**: Task-SA-04 (WeeklySelection records), Task-PM-01 (AuditLog)

---

### Task-SD-07: Thymeleaf Templates — Dashboard & Patient Detail

- **REQ**: SD-001 (Dashboard), SD-002 (Occupancy view), SD-004 (Patient list/detail)
- **What**: Create Thymeleaf server-rendered templates per STACK.md.
  - `staff/dashboard.html`:
    - **Weekly Selection panel**: "Last run: Monday 7:05 AM" + "Run Weekly Selection" button + confirmation dialog
    - **Selected Patients table**: Rank, Patient Name, Score, Slot time, Status (RESERVED/BOOKED)
    - **Non-selected (waitlisted) table**: Rank, Patient Name, Score, Days Overdue
    - **Reassign/Release buttons** per row
  - `staff/occupancy.html`:
    - **Overall occupancy bar**: 34/40 BOOKED (85%) with color
    - **By-Category cards**: PRENATAL 🟢 100%, CHILD 🟡 67%, CHRONIC 🔴 38%
    - **Week selector**: dropdown to view previous Weeks
    - **Export CSV button** for occupancy report
  - `staff/patient-detail.html`:
    - Patient info: name, CPF, status, contact
    - Categories with `targetDate` and `daysOverdue`
    - Appointment history (past + future)
    - Action buttons: "Suspend", "Reassign", "Release", "Complete Appointment"
  - `staff/audit-log.html`:
    - Filterable table: date range, action type, patient
    - CSV export button
  - `staff/system-health.html`:
    - Status indicators (green/red dots)
    - Recent errors list
  - **CSS**: Minimal — use Bootstrap or simple inline styles for Phase 1 MVP.
- **Where**:
  - Create: `src/main/resources/templates/staff/dashboard.html`
  - Create: `src/main/resources/templates/staff/occupancy.html`
  - Create: `src/main/resources/templates/staff/patient-detail.html`
  - Create: `src/main/resources/templates/staff/audit-log.html`
  - Create: `src/main/resources/templates/staff/system-health.html`
  - Create: `src/main/resources/templates/fragments/header.html` (shared header/nav)
  - Create: `src/main/resources/templates/fragments/footer.html` (shared footer)
  - Create: `src/main/resources/static/css/dashboard.css` (minimal styles)
- **Tests**: Thymeleaf integration tests covered in controller tests.
- **Verification**:
  - [ ] All templates render without errors
  - [ ] Thymeleaf expressions use CONTEXT.md canonical terms
  - [ ] Tables display data with correct formatting
  - [ ] Color-coded alerts on occupancy page
  - [ ] CSRF tokens on all POST forms
  - [ ] Responsive (usable on tablet for clinic staff)
- **Depends on**: Task-SD-01 through SD-06 (all controllers and services)

---

### Task-SD-08: Staff Dashboard Integration Tests

- **REQ**: SD-001 through SD-006 (full dashboard verification)
- **What**: End-to-end integration tests.
  1. `StaffDashboardIntegrationTest` (`@SpringBootTest` + H2):
     - **Weekly Selection flow**: create Slots → run Weekly Selection → view ranking → Reassign a Patient → Release a Patient
     - **Mark COMPLETED**: book Appointment → complete it → verify `lastConsultationDate` and `targetDate` updated
     - **Occupancy**: create 40 Slots → book 34 → verify 85% utilization
     - **Patient Suspend**: ACTIVE patient → suspend → verify excluded from next Weekly Selection
     - **Audit log**: perform bookings, cancellations, completions → query audit log → export CSV
     - **System health**: verify endpoint returns correct status
  2. Update `SpecConsistencyTest.REQUIRED_SPEC_FILES`.
- **Where**:
  - Create: `src/test/java/com/priorizasus/priorizasus/integration/StaffDashboardIntegrationTest.java`
  - Modificar: `src/test/java/com/priorizasus/priorizasus/harness/SpecConsistencyTest.java`
- **Verification**:
  - [ ] Complete staff workflow passes
  - [ ] Weekly Selection re-run works
  - [ ] Reassign and Release function correctly
  - [ ] Occupancy metrics accurate
  - [ ] Audit log comprehensive
  - [ ] CSV export produces valid output
- **Depends on**: Task-SD-01 through SD-07

---

## Cross-Cutting Concerns

Same as previous features.
**Additional**:
- **Staff user hardcoded** as "system" in Phase 1 (ADR-0004) — replace in Phase 2 with Spring Security.
- **No authentication** — dashboard is effectively open. Acceptable for single-clinic deployment behind clinic network (ADR-0004).
- **Thymeleaf SSR** — no SPA overhead. Server-rendered templates per PROJECT.md.
- **Color-coded alerts**: 🟢 ≥80%, 🟡 60–79%, 🔴 <60% per Category coverage.
- **All overrides logged**: Reassign, Release, Suspend → audit trail with reason.

---

**Status**: Ready for implementation
**Created**: May 24, 2026
