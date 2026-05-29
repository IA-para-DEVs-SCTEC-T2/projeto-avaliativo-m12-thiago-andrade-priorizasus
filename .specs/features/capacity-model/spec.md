# Capacity Model — Feature Specification

## Overview

The Capacity Model defines the clinic's weekly Appointment schedule: 40 BATCH Slots (30-minute each), distributed across 5 clinic days, all allocated by the Weekly Selection scoring algorithm. This feature creates Slots at the beginning of each Week and governs their availability rules.

## Requirements

### CM-001: Weekly Slot Creation
- **Trigger**: Scheduled task runs every Monday 7 AM (configurable)
- **Output**: 40 Slots created for the Week (Monday–Friday, 8 AM – 5 PM)
- **Slot structure**:
  - Slot ID (unique per Week)
  - Date + start time (e.g., 2026-05-27 08:00)
  - Duration = 30 minutes (fixed)
  - Type = BATCH (all 40)
  - Status = AVAILABLE, RESERVED, BOOKED, CANCELLED, EXPIRED
- **Acceptance**: All 40 Slots available at start of Week; correctly timestamped; assigned to correct type

### CM-002: Clinic Hours & Working Days
- **Working days**: Monday–Friday (no weekends)
- **Clinic hours**: 8 AM – 5 PM (9 hours = 18 slots per day × 5 days = 90 potential slots; capped at 40 actual slots)
- **Break handling** (if defined in future): Marked as unavailable (e.g., 12–1 PM lunch)
- **Acceptance**: Slots only created within clinic hours; no gaps between slots; no weekend slots

### CM-003: Slot Type Allocation
- **BATCH Slots (40 per Week)**: Allocated by the Weekly Selection algorithm to top-ranked Patients
  - Only eligible Patients (ACTIVE, within Category `targetDate` rules) can be selected
  - After Weekly Selection completes (Monday 7 AM), BATCH Slots are RESERVED for specific Patients
  - Unconfirmed BATCH Slots expire Friday 5 PM (RESERVED → EXPIRED)
- **Acceptance**: All 40 Slots allocated via Weekly Selection; type BATCH enforced in Booking module

### CM-004: Slot Availability State Machine
- **States**: AVAILABLE → RESERVED → BOOKED, with CANCELLED reachable from any state, and EXPIRED as terminal
- **Transitions**:
  - AVAILABLE: Waiting for booking or Weekly Selection allocation
  - RESERVED: Weekly Selection has allocated this BATCH Slot to a specific Patient (pessimistic lock held during allocation); only the reserved Patient may book it; expires Friday 5 PM if unconfirmed → EXPIRED
  - BOOKED: Appointment confirmed; Slot taken
  - CANCELLED: Appointment or Reservation cancelled by Patient or staff; Slot reverts to AVAILABLE (released for reuse)
  - EXPIRED: Unconfirmed BATCH Reservation after Friday 5 PM; terminal state, not reusable
- **Acceptance**: State transitions logged; each transition timestamped; audit trail queryable

### CM-005: Capacity Query & Availability Check
- **Capacity API**: `GET /api/capacity/week/{weekStart}` returns all 40 Slots with status
- **Availability check**: `GET /api/capacity/available?patientCategory={PRENATAL|CHILD|CHRONIC}` returns bookable Slots for a given Patient type
- **Filtering rules**:
  - BATCH Slots shown only to Patients selected by current Week's Weekly Selection
  - BOOKED Slots never shown
- **Acceptance**: API returns correct Slot count, times, types; filtering honors Weekly Selection

### CM-006: Slot Auditing
- **Log entry on each state change**: Timestamp, old status, new status, Patient ID (if applicable), staff user (if applicable)
- **Query capability**: Staff can view Slot occupancy history (e.g., "How many Slots were BOOKED vs. CANCELLED last Week?")
- **Acceptance**: Audit table populated; queryable; timestamps accurate

## Happy Path: Create Weekly Slots

1. Monday 7 AM: System scheduled task triggers
2. System creates 40 new Slot records for 2026-05-27 to 2026-05-31
3. Breakdown: 40 BATCH
4. All Slots have status = AVAILABLE
5. Weekly Selection runs, reserving 40 BATCH Slots to top-ranked eligible Patients
6. Patients see their RESERVED Slots in dashboard; can confirm Booking
7. By Friday 5 PM:
   - 34 Slots BOOKED (85% utilization)
   - 3 BATCH Slots CANCELLED (reverted to AVAILABLE; may be re-booked)
   - 3 unconfirmed RESERVED BATCH Slots → marked EXPIRED (terminal)

## Edge Cases & Constraints

| Case | Expected Behavior |
|------|-------------------|
| **Holidays** | Slots not created for holidays (e.g., Christmas); manual override in config |
| **Clinic closure day** | No Slots created; Weekly Selection skips that Week |
| **Double-booking attempt** | Prevented by pessimistic lock (see booking-system/spec.md) |
| **Cancelled Appointment** | Slot reverts to AVAILABLE; reusable by other eligible Patients |
| **Staff Reassign/Release** | Staff can manually move Patient from one Slot to another (Reassign) or free a Slot (Release); logs staff user + reason |
| **Slot expires unused** | BATCH Reservations unconfirmed by Friday 5 PM marked EXPIRED. EXPIRED is terminal — Slots are not re-used this Week or next |
| **Week has <40 eligible Patients** | Weekly Selection selects all eligible; remaining BATCH Slots stay AVAILABLE |
| **Partially booked week** | Occupancy metrics still calculated; reported to staff |

## Acceptance Criteria

- [ ] Weekly Slot creation task runs automatically (configurable schedule)
- [ ] 40 Slots created per Week, correctly timestamped, type-allocated (40 BATCH)
- [ ] Slots only created within clinic hours (8 AM–5 PM)
- [ ] Slot availability filtered correctly per Patient type and Weekly Selection
- [ ] State machine transitions enforced: AVAILABLE → RESERVED → BOOKED; CANCELLED from any; RESERVED→EXPIRED Friday 5 PM
- [ ] All state transitions logged to audit table
- [ ] Capacity API returns correct data; filtering works
- [ ] CANCELLED Slots revert to AVAILABLE; reusable
- [ ] Unused BATCH Slots and unconfirmed BATCH Reservations marked EXPIRED (terminal)
- [ ] Staff can query occupancy history per Week

---

**Dependency**: patient-master/spec.md (needs Patient Categories to filter Slot availability)  
**Depends on**: PROJECT.md vision, STACK.md tech stack  
**Related**: scoring-algorithm/spec.md (Weekly Selection reserves BATCH Slots), booking-system/spec.md (Patient books from available Slots)  
**Updated**: May 23, 2026 — Synced with grill-with-docs (Decisions #2, #3)
