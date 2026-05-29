# Scoring Algorithm — Feature Specification

## Overview

The Scoring Algorithm (Weekly Selection) selects 40 eligible Patients from (typically) >40 applicants for the weekly BATCH Slots. It implements the core fairness logic: each Patient receives a Score based on clinical Category (priority weight) plus `daysOverdue` their `targetDate`. The system ranks all eligible Patients and reserves the top 40 into BATCH Slots. Selection runs atomically every Monday 7 AM with a snapshot of eligibility at that moment.

## Requirements

### SA-001: Eligibility Rules (Snapshot Model)
- **Patient is eligible for Weekly Selection if** (evaluated at Monday 7 AM snapshot):
  - Status = ACTIVE
  - At least one active Category (PRENATAL, CHILD, or CHRONIC)
  - `targetDate` calculated (not missing data)
  - Not already BOOKED in current Week (RESERVED does not block — only BOOKED counts)
  - `lastConsultationDate` is ≥7 days ago (respects minimum interval between consultations)
- **Patient is NOT eligible if**:
  - Status = INACTIVE or SUSPENDED
  - No Category assigned
  - Already has a BOOKED Appointment in the current Week
- **Snapshot rule**: Eligibility is evaluated once at Monday 7 AM. Mid-week status changes do NOT retroactively affect the current Weekly Selection result. Patients retain their RESERVED Slots and can confirm them even if they later become ineligible (applies to next Weekly Selection).
- **Acceptance**: Eligibility criteria enforced at snapshot time; non-eligible Patients excluded from Weekly Selection automatically

### SA-002: Scoring Formula
- **Score per Patient**:
  ```
  Score = SUM(weight_i) + SUM(bonus_i)   for all active Categories i
  ```
- **Category Weight** (clinical priority, recalculated each Weekly Selection):
  - `PRENATAL (36+ weeks gest)`: +1000 points (highest risk)
  - `PRENATAL (28–36 weeks gest)`: +500 points
  - `PRENATAL (<28 weeks gest)`: +300 points
  - `CHILD (Day 7, Day 30 milestones)`: +900 points (newborns, critical window)
  - `CHILD (Month 2–12 milestones)`: +700 points (puericulture active)
  - `CHILD (Month 18, 24, annual)`: +400 points (development checks)
  - `CHRONIC`: +200 points (routine maintenance)
- **Days Overdue Bonus** (per Category, capped):
  - `daysOverdue_i = max(0, ChronoUnit.DAYS.between(targetDate_i, today))`
  - Count starts the day AFTER `targetDate_i` (i.e., on target date, daysOverdue = 0)
  - `bonus_i = min(daysOverdue_i × 10, 500)` — capped at 500 per Category
- **Multiple Categories**: Score = sum of all active Category weights + sum of per-Category overdue bonuses
  - Example: Maria (PRENATAL 36+w, 10 days overdue) + CHRONIC (20 days overdue)
    - Score = (1000 + 200) + (min(10×10, 500) + min(20×10, 500)) = 1200 + (100 + 200) = 1500
- **Acceptance**: Score calculated correctly per formula; auditable for each Patient per Weekly Selection

### SA-003: Ranking & Selection
- **Process**:
  1. Query all eligible Patients (snapshot at Monday 7 AM)
  2. Calculate Score for each (sum of Category weights + per-Category overdue bonuses)
  3. Sort by Score (descending)
  4. Select top 40 Patients
  5. Reserve 40 BATCH Slots via pessimistic locking (see design.md)
- **Tie-breaking rule**: If two Patients have identical Score:
  1. Earliest `targetDate` first (more urgent clinically)
  2. Earliest registration date (Patient ID ascending)
- **Acceptance**: Ranking is deterministic and reproducible; tie-breaking consistent

### SA-004: Weekly Selection Execution
- **Trigger**: Scheduled task (Monday 7 AM, configurable)
- **Transaction**: All-or-nothing (either all 40 BATCH Slots reserved successfully, or rollback on any error)
- **Locks**: Pessimistic locks acquired on BATCH Slots during selection (FOR UPDATE NOWAIT, 30s timeout); see design.md for lock strategy
- **Duration target**: <5 minutes total execution
- **Acceptance**: Weekly Selection completes atomically; no partial results; errors reported to staff

### SA-005: Transparency & Auditability
- **Score visible to staff**: Dashboard shows "Patient X, Score 1500, rank 5 of 35"
- **Non-selected list**: Staff can view "Patient Y, Score 650, rank 36 of 100" (waitlisted for next Week)
- **Rerun capability**: Staff can trigger Weekly Selection again if errors detected; previous Reservations released, recalculation with current snapshot
- **Acceptance**: Staff dashboard displays full Ranking; reasons for selection/non-selection clear

### SA-006: Accumulated Fairness (Waitlisted Patients)
- **`targetDate` is fixed during the Week**; `daysOverdue` increases each day naturally, but Score is NOT recalculated mid-week
- **Next Weekly Selection** (following Monday): All Patients' Scores are recalculated with updated `daysOverdue`
  - Waitlisted Patients gain ~7 days of `daysOverdue` → Score rises by ~70 points per Category per Week
  - Cap of 500 per Category prevents very overdue Patients from permanently dominating
- **Unconfirmed Reservations** (RESERVED BATCH Slots not BOOKED by Friday 5 PM): Slot → EXPIRED (terminal). Patient remains eligible for next Weekly Selection with increased `daysOverdue`
- **Acceptance**: Fairness accumulates over time; no Patient is permanently locked out; cap prevents runaway scores; waitlisted Patients gain priority each subsequent Week

## Happy Path: Weekly Selection

1. Monday 7 AM: Weekly Selection triggers
2. Query ACTIVE Patients with assigned Categories (assume 80 total)
3. Snapshot eligibility: 75 eligible (5 SUSPENDED or already BOOKED this Week)
4. Calculate Scores per Category:
   - Patient A: PRENATAL (36w gest, 10 daysOverdue) → weight=1000, bonus=min(10×10,500)=100, Score=1100 (rank 1)
   - Patient B: CHILD (Day 15 milestone, on time) → weight=900, bonus=0, Score=900 (rank 2)
   - Patient C: CHRONIC (70 daysOverdue, capped) → weight=200, bonus=min(70×10,500)=500, Score=700 (rank 3)
   - Patient D: PRENATAL (28w, 5 daysOverdue) + CHRONIC (20 daysOverdue) → weight=(500+200)=700, bonus=(50+200)=250, Score=950 (rank 1.5)
   - ... continues for all 75
5. Sort by Score descending; tie-break by earliest `targetDate`, then earliest registration
6. Select top 40
7. Reserve 40 BATCH Slots via pessimistic lock (system maps each Patient to a Slot)
8. Staff reviews result in dashboard: "40 Patients selected, 35 waiting"
9. Staff sees full Ranking; can Reassign or Release if needed
10. Patients see their RESERVED Slots in booking portal; confirm throughout the Week

## Edge Cases & Constraints

| Case | Expected Behavior |
|------|-------------------|
| **No eligible Patients** | Weekly Selection runs; selects 0 Patients; warns staff; BATCH Slots remain AVAILABLE |
| **Exactly 40 eligible** | All selected; no waiting list |
| **>80 eligible (2× capacity)** | Select top 40; remaining waitlisted; re-ranked next Week with accumulated `daysOverdue` |
| **Tied Score** | Tie-breaker: earliest `targetDate`, then earliest registration date |
| **Patient data changes mid-Weekly Selection** | Snapshot at Monday 7 AM used; any updates after selection start don't affect current Week's result |
| **Weekly Selection run twice by accident** | Second run releases previous Reservations, recalculates; only latest run's 40 Reservations valid |
| **Patient books 1 Slot, cancels, re-books another** | Only 1 BOOKED Appointment per Week allowed (enforced in Booking module); excess bookings rejected |
| **Patient becomes INACTIVE mid-week** | Already-reserved Slot retained (snapshot model); staff can Release manually; Patient excluded from next Weekly Selection |

## Acceptance Criteria

- [ ] Eligibility check filters ACTIVE Patients with Categories correctly (snapshot at Monday 7 AM)
- [ ] Score calculation: per-Category weight + per-Category `daysOverdue` bonus (capped 500 per Category) correct per spec
- [ ] Multiple Categories: Weights and bonuses summed independently per Category
- [ ] Ranking: Top 40 selected by descending Score
- [ ] Tie-breaking: Earliest `targetDate`, then earliest registration date; consistent and reproducible
- [ ] Weekly Selection execution: Atomic (all-or-nothing)
- [ ] Pessimistic locks acquired and released correctly (see design.md)
- [ ] Waitlisted Patients remain eligible for next Weekly Selection with accumulated `daysOverdue`
- [ ] Staff dashboard displays full Ranking + reasons for selection/non-selection
- [ ] Weekly Selection can be re-run; previous Reservations released
- [ ] Score audit trail: Queryable for every Patient, every Weekly Selection

---

**Dependency**: patient-master/spec.md (Categories, `targetDate`, `lastConsultationDate`), capacity-model/spec.md (BATCH Slots to reserve)  
**Depends on**: STACK.md (pessimistic locking, repository pattern)  
**Related**: booking-system/spec.md (Patients book RESERVED Slots), staff-dashboard/spec.md (staff views and runs Weekly Selection)  
**See also**: scoring-algorithm/design.md (algorithmic details, lock strategy, concurrency handling)  
**Updated**: May 23, 2026 — Synced with grill-with-docs (Decisions #1, #5, #6, #9)
