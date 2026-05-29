# Patient Master Data — Feature Specification

## Overview

The Patient Master module registers and categorizes all Patients under the clinic's responsibility. It establishes the foundation for all scheduling: a Patient must have at least one Category (PRENATAL, CHILD, or CHRONIC) and a tracked `targetDate` to be eligible for the Weekly Selection.

## Requirements

### PM-001: Patient Registration
- **Description**: Clinic staff register a new Patient via form (name, CPF, birth date, phone, address)
- **Input validation**: CPF format (11 digits, check digit); birth date not future; phone format
- **Acceptance**: Patient record created with `status = ACTIVE`; assigned unique ID; timestamp recorded
- **Error case**: Duplicate CPF rejected; user notified; form re-displayed with error message

### PM-002: Patient Categorization
- **Description**: Assign one or more clinical Categories to each Patient
- **Categories**:
  - `PRENATAL`: Pregnant Patients (needs `ultrasoundDate` + `gestationalWeeksAtUltrasound`; `targetDate` derived dynamically from current gestational weeks)
  - `CHILD`: Children 0–3 years old (needs `birthDate` + `lastMilestoneCompleted`; `targetDate` derived from next milestone per Ministry of Health table)
  - `CHRONIC`: Diabetes, hypertension, other conditions requiring 60-day follow-up (tracked with condition type)
- **Business rule**: One Patient can have multiple active Categories (e.g., pregnant + hypertensive)
- **Acceptance**: Category assigned; `targetDate` calculated; Patient eligible for Weekly Selection scoring

### PM-003: Target Date Calculation (`targetDate`)

- **Description**: System automatically calculates the next obligatory consultation deadline (`targetDate`) based on Category and clinical rules. This is distinct from `lastConsultationDate` (the factual date of the most recent completed Appointment).
- **PRENATAL** (dynamic, recalculated every Weekly Selection based on current gestational weeks):
  - `currentWeeks = gestationalWeeksAtUltrasound + (today - ultrasoundDate).days / 7.0`
  - <28 weeks: `targetDate = today + 30 days`
  - 28–36 weeks: `targetDate = today + 15 days`
  - 36+ weeks: `targetDate = today + 7 days`
- **CHILD** (Puericulture per Ministry of Health milestones):
  - Milestones: Day 7, Day 30, Month 2, 4, 6, 9, 12, 18, 24, then annual on birthday
  - `nextMilestone = getNextMilestone(lastMilestoneCompleted)`
  - `targetDate = birthDate + daysToMilestone(nextMilestone)`
  - `lastMilestoneCompleted` is updated after each completed Appointment
- **CHRONIC**:
  - `targetDate = lastConsultationDate + 60 days`
- **Acceptance**: `targetDate` calculated at registration and updated after each completed Appointment; visible in Patient dashboard
- **Edge case**: If Patient is past `targetDate`, `daysOverdue` reflects the number of days since the deadline, increasing priority in the Weekly Selection

### PM-004: Patient Status Lifecycle
- **Description**: Each Patient has a lifecycle status that determines eligibility for the Weekly Selection
- **Statuses**:
  - ACTIVE: Eligible for Weekly Selection; can book Appointments
  - INACTIVE: Permanently removed (moved, deceased); retained for audit only
  - SUSPENDED: Temporarily unavailable; can revert to ACTIVE
- **Acceptance**: Status transitions logged with timestamp + staff user; audit trail queryable

### PM-005: Last Consultation Tracking
- **Description**: After each Appointment is marked COMPLETED, update Patient's `lastConsultationDate` and recalculate `targetDate`
- **Acceptance**: Appointment marked COMPLETED; triggers recalculation; `targetDate` refreshed; next deadline calculated

### PM-006: Data Integrity
- **Constraint**: CPF is unique per system (only one Patient record per CPF)
- **Constraint**: INACTIVE Patients retain data for audit trail but never appear in queries
- **Constraint**: All timestamps (`created_at`, `updated_at`) stored in UTC; display in clinic timezone (`America/Sao_Paulo`)

## Happy Path: Register & Categorize Pregnant Patient

1. Staff navigates to "New Patient" form
2. Enters: Name = "Maria Silva", CPF = "12345678901", Birth = "1990-05-15", Phone = "11999999999"
3. Clicks "Register"
4. System validates CPF format, checks no duplicate exists
5. Patient created with ID = 1001, status = ACTIVE
6. System redirects to "Assign Category" form
7. Staff selects: Category = PRENATAL, Ultrasound Date = 2026-05-10, Gestational Weeks at Ultrasound = 24
8. System calculates: `currentWeeks = 24 + (today - 2026-05-10).days / 7.0 ≈ 25.9w`; since <28w, `targetDate = today + 30 days`
9. Staff clicks "Save"
10. Patient eligible for next Weekly Selection; status visible in staff dashboard

## Edge Cases & Constraints

| Case | Expected Behavior |
|------|-------------------|
| **Duplicate CPF** | Reject; message "Patient already registered"; link to edit existing record |
| **Birth date in future** | Reject; message "Birth date cannot be in the future" |
| **Patient switches Category** | Old Category marked inactive; new Category active; `targetDate` recalculated |
| **Overdue child (missed milestone)** | Child overdue by `(today - targetDate)`, shown as `daysOverdue`; included in Weekly Selection with high weight |
| **Multiple categories (pregnant + diabetic)** | Both `targetDate`s tracked; scored independently per Category; both considered in Weekly Selection |
| **Invalid phone format** | Warn user; allow save (phone optional, not blocking); flag for follow-up |
| **Patient becomes INACTIVE** | Record retained for audit; never queried for scheduling; no data loss |

## Acceptance Criteria

- [ ] New Patient registration form validates all fields (CPF, birth date, phone format)
- [ ] Duplicate CPF detection works; user re-directed to edit existing record
- [ ] Category assignment automatically calculates `targetDate` per Category rules
- [ ] `targetDate` visible in Patient dashboard and used in Weekly Selection algorithm
- [ ] `lastConsultationDate` updated after Appointment marked COMPLETED
- [ ] Status lifecycle (ACTIVE → INACTIVE/SUSPENDED) logged with timestamps
- [ ] All Patient queries filter by `status = ACTIVE` (except audit logs)
- [ ] CPF is unique constraint; database enforces
- [ ] Timestamps stored in UTC; display in local clinic time (`America/Sao_Paulo`)

---

**Dependency**: None (foundation feature)  
**Depends on**: PROJECT.md vision, STACK.md conventions  
**Related**: scoring-algorithm/spec.md (uses Patient Categories + `targetDate`/`lastConsultationDate`)  
**Updated**: May 23, 2026 — Synced with grill-with-docs (Decisions #4, #10, #11, #15)
