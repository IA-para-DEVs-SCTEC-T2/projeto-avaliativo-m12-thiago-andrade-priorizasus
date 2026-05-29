# Staff Dashboard — Feature Specification

## Overview

The Staff Dashboard provides clinic staff with:
1. **Weekly Selection management**: Trigger Weekly Selection, review results, Reassign/Release bookings
2. **Occupancy monitoring**: View Slot utilization, cancellation rates, no-show tracking
3. **Patient management**: View Patient list, Reassign/Release Appointments, mark Appointments as COMPLETED
4. **Reporting**: Export occupancy reports, Category coverage metrics

## Requirements

### SD-001: Weekly Selection Control Panel
- **View current Weekly Selection status**:
  - Last Weekly Selection timestamp
  - Number selected (e.g., "35 of 75 eligible selected")
  - Full Ranking table (Patient name, Score, rank, assigned Slot, status)
  - Non-selected waiting list (ranked, with reasons for non-selection)
- **Trigger Weekly Selection manually**: Button "Run Weekly Selection"
  - Confirmation dialog: "This will release previous Reservations and re-run selection"
  - On success: "Weekly Selection complete at 7:05 AM; 40 Patients selected"
  - On failure: "Weekly Selection failed: Slot lock timeout. Retry at 7:30 AM"
- **Reassign Patient Slot**: Staff can manually move Patient to different Slot
  - Use case: Patient called requesting reschedule; staff moves them
  - Action: `POST /api/staff/reassign` (patientId, newSlotId, reason)
  - Logging: staff user + timestamp + reason
- **Release Patient Slot**: Staff can cancel a Reservation or Appointment, freeing the Slot to AVAILABLE
  - Action: `POST /api/staff/release` (appointmentId, reason)
  - Distinct from Patient-initiated Cancellation
- **Acceptance**: Weekly Selection trigger works; results viewable; Reassign/Release capability present; all actions logged

### SD-002: Occupancy Dashboard
- **Weekly view**:
  - Total Slots: 40
  - BATCH booked: 34 (85%)
  - CANCELLED: 3
  - EXPIRED: 3 (unconfirmed BATCH)
  - Overall utilization: 85%
- **By Category**:
  - PRENATAL: 8 booked, 2 eligible but unbooked
  - CHILD: 12 booked, 3 eligible but unbooked
  - CHRONIC: 12 booked, 25 eligible but unbooked
- **Trends** (view previous Weeks):
  - Calendar dropdown: Select Week
  - Metrics recalculate for selected Week
- **Alerts** (color-coded):
  - 🟢 Green: Category well-covered (≥80% eligible booked)
  - 🟡 Yellow: Category at risk (60–79% booked)
  - 🔴 Red: Category critical (<60% booked)
- **Acceptance**: Dashboard calculates metrics correctly; alerts trigger appropriately; filtering by Week works

### SD-003: Mark Appointment as COMPLETED
- **Actor**: Staff (nurse/receptionist/admin) marks Appointment after Patient consultation
- **Endpoint**: `POST /api/staff/appointments/{appointmentId}/complete`
- **Request payload**:
  ```json
  {
    "statusAtArrival": "PRESENT",
    "completedAt": "2026-05-28T12:30:00Z",
    "notesFromConsultation": "Routine checkup, all normal"
  }
  ```
- **On successful completion**:
  - Appointment status → COMPLETED
  - `patient.lastConsultationDate` → today
  - `patient.targetDate` → recalculated per Category rules
  - Audit log entry created

### SD-004: Patient Management
- **Patient list**:
  - Table: Patient ID, Name, Category, `lastConsultationDate`, `targetDate`, `daysOverdue`, Status
  - Filter by: Category, Status, Overdue status (on-time, overdue <7 days, overdue >7 days)
  - Search by: Name, CPF
- **Patient detail view**:
  - All Appointments (past + future)
  - Current Category + `targetDate`
  - Contact info (phone, address)
  - Action buttons: "Reassign", "Release", "Suspend Patient"
- **Suspend Patient**: `POST /api/staff/suspend-patient` (patientId, reason)
  - Patient status = SUSPENDED; no longer eligible for Weekly Selection
  - Existing Appointments retained (history)
  - Staff note visible in Patient record
- **Acceptance**: Patient list filterable/searchable; detail view complete; Suspend action works; audit logged
- **Cancellation log**: View all cancellations (date, Patient name, Slot time, reason if provided)
- **No-show tracking** (Phase 2 feature; stub now):
  - If Appointment time passes + not marked COMPLETED, flag as "Assumed No-Show"
  - Staff can confirm or override
- **Cancellation rate**: Display percentage (e.g., "8% of booked Slots cancelled")
- **Acceptance**: Cancellations logged; queryable; no-show flag present (Phase 2 implementation deferred)

### SD-005: Audit Trail & Reporting
- **Audit log viewer**: `GET /api/staff/audit-log`
  - Filters: Date range, action type (WEEKLY_SELECTION, BOOKING, REASSIGN, RELEASE, CANCELLATION, SUSPENSION, COMPLETED), Patient
  - Table: Timestamp (UTC), Action, Staff User, Patient, Details
- **Export reports**: CSV download
  - Occupancy report (weekly metrics)
  - Category coverage report (% of eligible booked per Category)
  - Cancellation report (no-show rate, cancellation reasons)
- **Acceptance**: Audit log queryable; export works; CSV format valid

### SD-006: System Health
- **Status indicator**:
  - Database connection ✓/✗
  - Last Weekly Selection time + status
  - Scheduled tasks enabled ✓/✗
- **Error log**: Recent errors (transaction failures, lock timeouts, database errors)
- **Acceptance**: Status page displays; health checks work; errors visible for debugging

## Happy Path: Staff Reviews & Runs Weekly Selection

1. Monday 6:55 AM: Staff logs into dashboard
2. Dashboard shows: "Last Weekly Selection: Friday 1 Week ago"
3. Staff clicks "Run Weekly Selection" button
4. Confirmation dialog: "Release previous selections and re-run? 75 Patients eligible."
5. Staff clicks "Confirm"
6. System runs Weekly Selection (≈30 seconds)
7. Staff sees: "✓ Weekly Selection completed at 7:02 AM; 40 Patients selected, 35 waiting"
8. Staff expands "Selected Patients" table:
   - Rank 1: Maria Silva, Score 1500, Tuesday 9:00 AM (RESERVED)
   - Rank 2: João Santos, Score 900, Tuesday 9:30 AM (RESERVED)
   - ... (35 rows)
9. Staff notices: "Rank 3: Pedro Oliveira, Score 890, status=INACTIVE (RED FLAG)"
10. Staff clicks "Release" on Pedro's row
11. Dialog: "Patient status changed to INACTIVE after Weekly Selection. Release Slot?"
12. Staff clicks "Release"
13. Slot reverts to AVAILABLE; Pedro's Reservation cancelled
14. Staff expands "Non-Selected" (waiting list):
    - Rank 36: Ana Costa, Score 850, overdue 8 days
    - Rank 37: Carlos Melo, Score 805, overdue 12 days
    - ... (40 rows)
15. Staff notes: "Looks fair; CHILD Category has 3 waiting (YELLOW alert)."
16. Staff checks "Occupancy Dashboard" tab:
    - PRENATAL: 3 booked, 3 eligible → 100% 🟢
    - CHILD: 8 booked, 12 eligible → 67% 🟡
    - CHRONIC: 23 booked, 60 eligible → 38% 🔴

## Happy Path: Staff Marks Appointment as COMPLETED

1. Tuesday 9:30 AM: Maria Silva finishes her consultation
2. Staff navigates to Patient detail → Maria Silva
3. Staff clicks "Complete Appointment" on today's 9:00 AM Appointment
4. Dialog shows: "Mark as PRESENT / NO_SHOW?"
5. Staff selects "PRESENT", adds note: "Routine prenatal check, all normal"
6. System updates:
   - Appointment status → COMPLETED
   - `patient.lastConsultationDate` → today
   - `patient.targetDate` → recalculated (PRENATAL 28w → +15 days)
   - Audit log entry created
7. Maria's Patient record shows updated `lastConsultationDate` and new `targetDate`

## Edge Cases & Constraints

| Case | Expected Behavior |
|------|-------------------|
| **Staff runs Weekly Selection twice accidentally** | Second selection recalculates; previous Reservations released; no double-reserved Slots |
| **Weekly Selection runs while Patient is booking** | Pessimistic lock prevents both operations from interfering; one succeeds, other fails with timeout |
| **Staff tries to Reassign Patient to already-BOOKED Slot** | UI prevents (Slot shown as BOOKED, greyed out); if forced via API, database FK constraint prevents |
| **Patient status changes to INACTIVE mid-Week** | Snapshot model used; inactive Patient may have Reservation from pre-Weekly Selection query; staff can Release |
| **No eligible Patients this Week** | Weekly Selection runs; selects 0; all BATCH Slots remain AVAILABLE; staff notified (no error) |
| **Cancellation/no-show rate > 50%** | Alert: "Unusually high cancellations this Week; contact Patients to confirm?" (Phase 2) |
| **Export report for Week with no data** | CSV generated with headers but 0 rows; no error |
| **Database down during Weekly Selection** | Selection fails; transaction rolled back; locks released; error logged; staff notified |

## Acceptance Criteria

- [ ] Weekly Selection trigger button executes Weekly Selection algorithm
- [ ] Weekly Selection results displayed: Selected + non-selected Ranking tables
- [ ] Reassign: Staff can manually move Patient to different Slot
- [ ] Release: Staff can cancel a Reservation/Appointment, freeing Slot to AVAILABLE
- [ ] Mark COMPLETED: Staff can mark Appointment as COMPLETED; updates `lastConsultationDate` + `targetDate`
- [ ] Occupancy dashboard: Calculates % booked per Category + overall utilization
- [ ] Category alerts: Color-coded (green ≥80%, yellow 60–79%, red <60%) per coverage %
- [ ] Cancellation tracking: Log visible; cancellation rate calculated
- [ ] Patient list: Filterable by Category/status, searchable by name/CPF
- [ ] Patient detail: Shows all Appointments, `targetDate`/`lastConsultationDate`, contact info
- [ ] Suspend Patient: Changes status to SUSPENDED; no longer eligible for Weekly Selection; audit logged
- [ ] Audit log: Queryable by date range, action type, Patient
- [ ] Export to CSV: Occupancy, coverage, cancellation reports
- [ ] System health: Database status, last Weekly Selection time, scheduled tasks status visible
- [ ] Error log: Recent errors displayed; helps staff debug

---

**Dependency**: patient-master/spec.md, capacity-model/spec.md, scoring-algorithm/spec.md, booking-system/spec.md  
**Depends on**: STACK.md (repository queries, transaction management)  
**Related**: All other features (staff interacts with all modules)  
**Updated**: May 23, 2026 — Synced with grill-with-docs (Decisions #14)
