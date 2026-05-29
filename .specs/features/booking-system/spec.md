# Booking System — Feature Specification

## Overview

The Booking System allows eligible Patients to claim their RESERVED BATCH Slots. It enforces pessimistic locking to prevent double-booking, validates Patient eligibility, and creates confirmed Appointments.

## Requirements

### BK-001: Patient Slot Visibility (Labeled Visibility)
- **BATCH Slots**: Visible to all Patients, but with status label indicating bookability
  - `RESERVED_FOR_ME`: Patient was selected in current Week's Weekly Selection (rank ≤40) — BOOKABLE
  - `RESERVED_FOR_ANOTHER`: Reserved for a different Patient — NOT bookable (🔒)
  - Query: `GET /api/booking/my-available-slots` returns Slots with visibility labels
- **My Appointments**: `GET /api/booking/my-appointments` shows Patient's current + past Appointments
- **Acceptance**: Slot visibility enforced; filtering correct per Slot type; labels accurate

### BK-002: Booking Request & Validation
- **Request**: `POST /api/booking/reserve` with `{ patientId, slotId }`
- **Validations** (in order):
  1. Patient exists and status = ACTIVE
  2. Patient not already BOOKED in target Week (max 1 BOOKED Appointment per Week; RESERVED Slots do NOT count toward this limit)
  3. Slot exists, status = AVAILABLE or RESERVED, not BOOKED or EXPIRED
  4. If Slot type = BATCH: Patient must be in current Weekly Selection (rank ≤40) AND Slot must be RESERVED for this Patient
  5. Slot time not in the past (cannot book past Appointments)
- **Acceptance**: All validations passed; Booking proceeds; validation failures return HTTP 400/403 with error message

### BK-003: Pessimistic Locking During Booking
- **Lock strategy**: Acquire exclusive lock on Slot before confirming Booking
- **Pseudo-SQL**:
  ```
  SET lock_timeout = '30s';
  BEGIN TRANSACTION;
  SELECT * FROM Slot WHERE id = :slotId FOR UPDATE NOWAIT;
  -- If lock acquired:
  UPDATE Slot SET patient_id = :patientId, status = 'BOOKED' 
  WHERE id = :slotId;
  CREATE Appointment (patient_id, slot_id, status='CONFIRMED', timestamp);
  COMMIT;  -- Lock released
  ```
- **Concurrency**: If two Patients click "Book Slot 5" simultaneously:
  - First request acquires lock, updates Slot, commits (200 OK)
  - Second request finds Slot locked, fails immediately with NOWAIT (409 Conflict)
  - Patient UI shows: "Slot already booked, try another slot"
- **Acceptance**: Lock acquired & released correctly; double-booking prevented; NOWAIT handled gracefully

### BK-004: Appointment Creation
- **On successful Booking**:
  - Appointment record created with status = CONFIRMED
  - Slot status updated to BOOKED
  - Patient notified (Phase 2 feature; now: stub)
  - Appointment visible in Patient dashboard
- **Confirmation details**: Appointment ID, Patient name, Slot date/time, clinic address
- **Acceptance**: Appointment record created; visible in Patient dashboard; Appointment unique per Slot

### BK-005: Booking Cancellation
- **Patient cancellation**: `DELETE /api/booking/appointments/{appointmentId}`
  - Patient can cancel up to 24 hours before Appointment
  - Slot reverts to AVAILABLE
  - Appointment marked CANCELLED
  - Slot becomes available for other Patients
- **Staff Release** (distinct from Patient cancellation): Staff can Release any Appointment with reason logged
- **Late cancellation**: If <24 hours before Slot, UI warns "Cancelling will lose this slot" but allows (staff can adjust)
- **Acceptance**: Cancellation releases Slot; reverts to AVAILABLE; timestamp logged

### BK-006: Booking Constraints
- **Max 1 BOOKED Appointment per Week per Patient**: Prevents double-booking same Week
  - RESERVED Slots do NOT count — only BOOKED Appointments count toward the limit
  - Query: Check if Patient already has BOOKED Appointment in target Week (`week_start`)
  - Booking denied if true (error: "You already have an Appointment this Week")
  - Patient may have 1 RESERVED (from Weekly Selection) + 1 BOOKED simultaneously
- **Slot occupancy**: Only 1 Patient per 30-min Slot (enforced by database FK constraint)
- **No overbooking past Slots**: Cannot book Appointment with start time in past
- **Acceptance**: 1-BOOKED-per-Week enforced; cannot book past appointments; occupancy enforced

## Happy Path: Patient Books BATCH Slot

1. Monday 7 AM: Weekly Selection runs; Patient A selected (rank 5)
2. Patient A logs into booking portal
3. Portal shows: "Your RESERVED slots this week:"
4. Patient sees:
   - Tuesday 9:00 AM ✓ `RESERVED_FOR_ME` (BOOKABLE)
   - Tuesday 9:30 AM ✓ `RESERVED_FOR_ME` (BOOKABLE)
   - Other Patients' BATCH Slots: 🔒 `RESERVED_FOR_ANOTHER` (not clickable)
5. Patient clicks "Book" on Tuesday 9:00 AM
6. System validates:
   - Patient A is ACTIVE ✓
   - Not already BOOKED this Week ✓ (only RESERVED, which doesn't count)
   - Slot is RESERVED for Patient A ✓
   - Patient in Weekly Selection ✓
7. System acquires pessimistic lock on Slot (FOR UPDATE NOWAIT)
8. System creates Appointment (status = CONFIRMED)
9. System updates Slot (status = BOOKED, patient_id = A)
10. Transaction committed; lock released
11. HTTP 200 returned; Patient sees: "Appointment confirmed for Tuesday 9:00 AM"
12. Appointment appears in Patient's dashboard

## Edge Cases & Constraints

| Case | Expected Behavior |
|------|-------------------|
| **Patient not in Weekly Selection tries to book BATCH Slot** | HTTP 403: "You are not eligible for this slot; wait for next Weekly Selection" |
| **Patient tries to book another Patient's RESERVED BATCH Slot** | HTTP 403: "This slot is RESERVED_FOR_ANOTHER" |
| **Slot already BOOKED** | Pessimistic lock NOWAIT → 409 Conflict: "Slot already booked by another Patient; try another slot" |
| **Patient already has 1 BOOKED Appointment this Week** | HTTP 400: "You already have an Appointment on Tuesday; max 1 BOOKED per Week" |
| **Patient has 1 RESERVED + 0 BOOKED; tries to book another BATCH** | Allowed if RESERVED for this Patient (RESERVED doesn't count toward limit) |
| **Past Slot time** | HTTP 400: "Cannot book Appointment in the past" |
| **Patient SUSPENDED or INACTIVE** | HTTP 403: "Your account is not active; contact clinic staff" |
| **Cancellation <24h before** | Warning shown; allowed; staff notified for potential no-show tracking (Phase 2) |
| **Last-minute cancellation (5 min before)** | Allowed; Slot released immediately to AVAILABLE |
| **Patient books, then status changes to INACTIVE** | Appointment still CONFIRMED (snapshot model); staff can Release if needed |

## Acceptance Criteria

- [ ] BATCH Slots visible with `RESERVED_FOR_ME` / `RESERVED_FOR_ANOTHER` labels
- [ ] Booking validation: All checks enforced (Patient status, 1 BOOKED/week, Slot status, Weekly Selection check, time check, past check)
- [ ] 1 BOOKED Appointment per Week enforced; RESERVED Slots NOT counted
- [ ] Pessimistic lock acquired on Slot before Booking (FOR UPDATE NOWAIT)
- [ ] Lock NOWAIT returns 409 Conflict with user-friendly message
- [ ] Appointment record created on successful Booking
- [ ] Slot status updated to BOOKED (not RESERVED or AVAILABLE)
- [ ] Patient can see their Appointments in dashboard
- [ ] Cancellation releases Slot back to AVAILABLE
- [ ] All Booking + Cancellation events logged to audit table

---

**Dependency**: patient-master/spec.md (Patient eligibility), capacity-model/spec.md (Slots), scoring-algorithm/spec.md (Weekly Selection status)  
**Depends on**: STACK.md (pessimistic locking, repository pattern)  
**Related**: staff-dashboard/spec.md (staff views bookings + Reassign/Release)  
**Updated**: May 23, 2026 — Synced with grill-with-docs (Decisions #7, #8, #13)
