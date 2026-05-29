# PRIORIZASUS

A fair, data-driven appointment scheduling system for ESF (Family Health Strategy) clinics. Scores patients by clinical urgency and days overdue their target consultation window, then allocates 40 weekly slots via algorithmic batch selection.

> For development harness, architecture guardrails, and CI/CD pipeline documentation, see **[docs/HARNESS.md](docs/HARNESS.md)**.

## Language

### Core Entities

**Patient**:
A person registered in the clinic's catchment area who may receive medical appointments. A Patient has a status (ACTIVE, INACTIVE, or SUSPENDED) and zero or more Categories.
_Avoid_: Client, User, Enrollee, Beneficiary, Applicant

**Category**:
A clinical grouping assigned to a Patient that determines their consultation frequency and scoring weight. A Patient may have multiple active Categories simultaneously (e.g., PRENATAL + CHRONIC). The three categories are: PRENATAL (pregnant), CHILD (puericulture, 0–3 years), and CHRONIC (diabetes, hypertension, ongoing conditions).
_Avoid_: Condition, Clinical Program, Cohort, Risk Group, Stratum

### Slot Lifecycle

**Slot**:
A 30-minute appointment window within clinic hours (Monday–Friday, 8 AM–5 PM). Each week, 40 BATCH Slots are created. A Slot has one Status at any time and is assigned to at most one Patient.
_Avoid_: Time Slot, Appointment Slot, Window, Opening

**BATCH Slot**:
A Slot allocated via the weekly scoring algorithm. Only Patients selected by the Weekly Selection (top 40 by Score) may book BATCH Slots. Allocation is determined Monday 7 AM; unconfirmed BATCH Slots expire Friday 5 PM.
_Avoid_: SCHEDULED, Pre-allocated, Algorithmic Slot

**Slot Status**:
The state of a Slot in its lifecycle. Valid states: AVAILABLE (unclaimed), RESERVED (batch-allocated to a specific Patient, awaiting confirmation), BOOKED (Appointment confirmed), CANCELLED (released back to AVAILABLE), EXPIRED (unconfirmed BATCH after Friday 5 PM). Transitions are logged with timestamp.
_Avoid_: Free/Occupied, Open/Closed

### Scoring & Selection

**Weekly Selection**:
The algorithmic process that runs every Monday 7 AM to allocate BATCH Slots. It fetches all eligible Patients, calculates their Score, ranks them, and reserves the top 40 into BATCH Slots. Runs atomically (all-or-nothing) with pessimistic locking (`SELECT ... FOR UPDATE NOWAIT`). Produces a Selection Result visible to staff.
_Avoid_: Batch, Batch Run, Scoring Run, Algorithm Run

**Score**:
A number representing a Patient's priority for the Weekly Selection. Formula: `Score = sum(Category Weight) + sum(daysOverdue × 10)`, where `daysOverdue = max(0, days between targetDate and today)`. The `daysOverdue` bonus is capped at 500 per Category to prevent very overdue Patients from permanently dominating. A Patient with multiple Categories has their weights and bonuses summed independently.
_Avoid_: Priority, Points, Rank Value, Selection Score

**Category Weight**:
The base priority points assigned to a Category, reflecting clinical urgency. Current weights: PRENATAL 36+ weeks = 1000, PRENATAL 28–36 weeks = 500, PRENATAL <28 weeks = 300, CHILD 0–30 days = 900, CHILD 1–12 months = 700, CHILD 1–3 years = 400, CHRONIC = 200. Weights are recalculated dynamically each Weekly Selection (e.g., gestational weeks advance, changing the weight).
_Avoid_: Priority Weight, Base Score, Clinical Weight

**Ranking**:
The ordered list of all eligible Patients by descending Score, produced during each Weekly Selection. Tie-breaking: earliest `targetDate` first, then earliest registration date. The Ranking is visible to staff for audit and transparency — Patients ranked 36+ are waitlisted for the following week.
_Avoid_: Leaderboard, Ordered List, Selection Order

**Selection**:
A single assignment of one Patient to one BATCH Slot within a Weekly Selection. A Selection has a status: SELECTED (reserved, awaiting confirmation), BOOKED (Patient confirmed), or RELEASED (staff override or Patient cancelled). All Selections from a Weekly Selection are logged for audit.
_Avoid_: Allocation, Assignment, Reservation Entry

### Booking & Appointments

**Reservation**:
A BATCH Slot temporarily held for a specific Patient by the Weekly Selection. The Slot has status RESERVED and the Patient has until Friday 5 PM to confirm. Unconfirmed Reservations expire (Slot → EXPIRED). Only the reserved Patient may book that Slot. A Reservation is not yet an Appointment — it is a promise of availability.
_Avoid_: Hold, Pre-booking, Soft Booking, Tentative Appointment

**Booking** (verb):
The Patient's action of confirming a Reservation. Successful Booking creates an Appointment with status CONFIRMED. The system enforces: max 1 Appointment per Patient per week, pessimistic lock (`SELECT ... FOR UPDATE NOWAIT`) on the Slot, and validation that the Patient is eligible (ACTIVE and selected in the Weekly Selection for that BATCH Slot).
_Avoid_: Reserving (as a verb — reserve is what the algorithm does), Scheduling, Confirming

**Appointment**:
A confirmed medical visit between a Patient and the doctor at a specific Slot. Created when Booking succeeds. Has a status: CONFIRMED (upcoming), COMPLETED (patient attended, `lastConsultationDate` updated), CANCELLED (released by Patient or staff, Slot reverts to AVAILABLE), or NO_SHOW (patient did not attend — Phase 2). An Appointment belongs to exactly one Slot and one Patient.
_Avoid_: Consult, Visit, Session, Booking (noun)

### Staff Operations

**Override**:
An umbrella term for any staff-initiated change to the Weekly Selection result or an existing Appointment. All Overrides are logged with staff user, timestamp, and reason for audit.
_Avoid_: Admin Action, Manual Fix, Force Change

**Reassign**:
A staff action that moves a Patient's Reservation or Appointment from one Slot to another. The original Slot is Released. Used when a Patient requests rescheduling or staff identifies a scheduling conflict. Logs: staff user, Patient, old Slot, new Slot, reason.
_Avoid_: Move, Swap, Transfer, Reschedule (ambiguous — could mean Patient-initiated)

**Release**:
A staff action that cancels a Reservation or Appointment, freeing the Slot to AVAILABLE. Used when a Patient becomes INACTIVE mid-week, a Reservation was created in error, or staff needs to free capacity. Distinct from Patient-initiated Cancellation — logged separately.
_Avoid_: Cancel (ambiguous — Patient or staff?), Free, Clear, Drop

**Suspend** (Patient):
A staff action that changes a Patient's status from ACTIVE to SUSPENDED, temporarily removing them from Weekly Selection eligibility. Existing Appointments are retained. Logs: staff user, Patient, reason.
_Avoid_: Deactivate, Disable, Block, Pause

### Operational Terms

**Week**:
A clinic week, defined by its Monday date (`weekStart`). Runs Monday–Friday. All Slots, Weekly Selections, and Appointments are scoped to a Week. The "1 Appointment per Patient per Week" rule uses `weekStart` for grouping. Example: weekStart = 2026-05-27 covers slots from May 27–31.
_Avoid_: Calendar Week, Slot Week, Batch Week

**Occupancy**:
The percentage of Slots that became BOOKED in a given Week. Formula: `(BOOKED Slots / 40) × 100`. Tracked per Category (e.g., "PRENATAL occupancy = 80%") and overall. Staff dashboard displays color-coded alerts: green (≥80%), yellow (60–79%), red (<60%).
_Avoid_: Utilization, Fill Rate, Booking Rate

**Audit Trail**:
The immutable log of all system actions: Weekly Selections, Bookings, Cancellations, Overrides, status transitions. Queryable by date range, action type, Patient, and staff user. Supports CSV export for reporting. Every entry has a timestamp in UTC.
_Avoid_: Log, History, Event Log, Activity Feed

**No-Show**:
An Appointment where the Patient did not attend and staff marked it as such (Phase 2). In Phase 1, an Appointment past its time with status CONFIRMED is flagged for staff review but not automatically marked.
_Avoid_: Missed Appointment, Absence, Did Not Attend (DNA)

**Timezone Handling**:
All timestamps (Appointment times, Selection timestamps, audit entries) are stored in UTC. Display is converted to the clinic's local timezone (`America/Sao_Paulo`). Duration calculations (`daysOverdue`, Week boundaries) use `LocalDate` to avoid daylight saving time ambiguity. Database configured with `hibernate.jdbc.time_zone=UTC`.
_Avoid_: Local time storage, OffsetDateTime for durations

## Example Dialogue

**Dev**: When does a Patient become eligible for the Weekly Selection?

**Domain Expert**: A Patient is eligible if they're ACTIVE, have at least one Category, and haven't had a consultation in the last 7 days. The 7-day gap is based on `lastConsultationDate` — we don't want patients coming in every few days and crowding out those who haven't been seen.

**Dev**: And how do you decide who gets a BATCH Slot?

**Domain Expert**: Every Monday at 7 AM, the Weekly Selection runs. It calculates a Score for every eligible Patient — that's the sum of their Category Weights plus a bonus for each day they're past their `targetDate`. We cap the bonus at 500 per Category so someone who's a year overdue doesn't permanently block everyone else. Then we rank them and the top 40 get Reservations.

**Dev**: What if a pregnant Patient is also diabetic?

**Domain Expert**: She gets both Category Weights summed. If she's 36+ weeks pregnant and 20 days overdue on her chronic care, her Score is 1000 (PRENATAL 36+) + 200 (CHRONIC) + 200 (20 days × 10 for prenatal) + 200 (20 days × 10 for chronic) = 1600. Both needs are real, so both count.

**Dev**: And what happens if she doesn't confirm by Friday?

**Domain Expert**: Her Reservation expires Friday at 5 PM. The Slot goes to EXPIRED. She stays eligible for next week's Weekly Selection, and her `daysOverdue` will have gone up by 7 days, so her Score will be higher — she's more likely to get selected next Monday.

### Patient Dates & Status

**`lastConsultationDate`**:
The factual date of a Patient's most recently completed consultation. Updated every time an Appointment is marked COMPLETED. This is historical data — it is never "overdue."
_Avoid_: Last Visit, Last Appointment Date, Last Seen

**`targetDate`**:
The deadline by which a Patient's next consultation must occur. Calculated automatically based on Category rules: PRENATAL (7/15/30 days depending on gestational weeks), CHILD (milestone-based: Day 7, Day 30, Month 2/4/6/9/12/18/24, then annual), CHRONIC (60 days). The scoring algorithm uses `targetDate` to compute `daysOverdue`. Recalculated after every completed Appointment.
_Avoid_: Clinical Window, Due Date, Next Consultation Date, Deadline, Follow-up Date

**Patient Status**:
The lifecycle state of a Patient. Valid values: ACTIVE (eligible for batch selection), INACTIVE (permanently removed — moved, deceased), SUSPENDED (temporarily unavailable). Only ACTIVE Patients appear in batch selection queries. Status transitions are logged with timestamp and staff user for audit.
_Avoid_: Enabled/Disabled, Active/Deactivated, Blocked

## AI-Native Practices

This project is designed for AI-assisted development. All tooling, conventions, and workflows assume an AI agent is part of the development loop:

- **Spec-Driven Development**: Every feature originates in `.specs/features/{feature}/spec.md` with numbered REQ-IDs and acceptance criteria. No code is written without a spec.
- **`@ReqId` Annotation**: Every public service method carries `@ReqId("XX-NNN")` linking code to its specification requirement. Verified by `SpecDriftDetectionTest`.
- **Canonical Terminology**: All code, docs, and specs MUST use the canonical terms defined in this glossary. Avoid terms are forbidden — the `ai-plan.yml` CI pipeline flags violations.
- **Semantic Fix Loop**: The AI agent implements → Spotless formats → tests run → SpecDriftDetectionTest verifies alignment → semantic validation confirms business rules → fixes any drift (max 3 retries before human escalation).
- **CI Gates**: Every change flows through `ai-plan` → `ai-build` → `spec-drift` → `ai-review` → `evidence-log` → merge. Each gate validates a different dimension of correctness.
- **Agent Instructions**: `.github/copilot-instructions.md` defines Plan, Implement, and Review modes with mode-specific rules and forbidden actions.
- **Skill Lock**: `skills-lock.json` pins installed Copilot skills with integrity hashes for reproducible AI-assisted workflows across machines.
- **Harness Tests**: `ArchitectureTest` (ArchUnit), `SpecConsistencyTest`, and `SpecDriftDetectionTest` run as part of every build — they are NOT optional.
