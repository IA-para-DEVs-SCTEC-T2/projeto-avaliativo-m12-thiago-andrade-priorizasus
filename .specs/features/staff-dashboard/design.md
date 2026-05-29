# Staff Dashboard — Design Document

## Dashboard Layout

```
┌──────────────────────────────────────────────────────────────────────┐
│ STAFF DASHBOARD                                                       │
├──────────────────────────┬───────────────────────────────────────────┤
│ WEEKLY SELECTION CONTROL │ OCCUPANCY PANEL                            │
│                          │                                            │
│ [Run Weekly Selection]   │ ┌────────┬────────┬────────┬────────────┐ │
│                          │ │PRENATAL│ CHILD  │CHRONIC │  OVERALL   │ │
│ Last run: Mon 7:05 AM    │ │ 🟢 80% │ 🟡 65% │ 🔴 55% │  🟡 73%   │ │
│ Selected: 40/75          │ └────────┴────────┴────────┴────────────┘ │
│                          │                                            │
│ ┌──────────────────────┐ │ SLOT GRID                                 │
│ │ RANKING TABLE        │ │ ┌──────┬──────┬──────┬──────┬──────┐     │
│ │ #1  Maria  Score 1500│ │ │Mon   │Tue   │Wed   │Thu   │Fri   │     │
│ │ #2  João   Score 1420│ │ │8:00✓ │8:00✓ │8:00✓ │8:00✓ │8:00✓ │     │
│ │ ...                   │ │ │8:30✓ │8:30✓ │8:30✓ │8:30✓ │8:30✓ │     │
│ │ #40 Ana   Score 680  │ │ │ ...  │ ...  │ ...  │ ...  │ ...  │     │
│ │ ─── WAITLIST ────────│ │ └──────┴──────┴──────┴──────┴──────┘     │
│ │ #41 Pedro Score 675  │ │ ✓ = BOOKED  ◷ = RESERVED  ✗ = EXPIRED    │
│ └──────────────────────┘ │                                            │
├──────────────────────────┴───────────────────────────────────────────┤
│ PATIENT MANAGEMENT │ AUDIT TRAIL │ REPORTS                            │
└──────────────────────────────────────────────────────────────────────┘
```

## Weekly Selection Control Flow

```
STAFF CLICKS [Run Weekly Selection]
  │
  ▼
Confirmation Dialog: "This will release previous Reservations and re-run selection.
                      Continue?"
  │
  ├─ [Cancel] → No action
  │
  └─ [Confirm] → POST /api/staff/weekly-selection/run
                    │
                    ▼
                  ScoringService.executeWeeklySelection(weekStart)
                    │
                    ├─ SUCCESS → "Weekly Selection complete. 40 Patients selected."
                    │              Refresh Ranking table + Occupancy panel
                    │
                    └─ FAILURE → "Weekly Selection failed: {reason}"
                                  Show retry button
```

## Occupancy Color-Code Algorithm

```
function getOccupancyColor(categoryOccupancy):
    percentage = (categoryOccupancy.booked / categoryOccupancy.eligible) × 100

    if percentage ≥ 80:
        return GREEN   // Category well-covered
    elif percentage ≥ 60:
        return YELLOW  // Category at risk
    else:
        return RED     // Category critical

function getOverallOccupancyColor(week):
    booked = countSlotsByStatus(week, BOOKED)
    percentage = (booked / 40) × 100  // 40 = total BATCH slots

    if percentage ≥ 80: return GREEN
    elif percentage ≥ 60: return YELLOW
    else: return RED
```

## Reassign Flow

```
STAFF selects Patient → clicks [Reassign]
  │
  ▼
Reassign Dialog:
  Current Slot: Tuesday 9:00 AM
  New Slot:     [dropdown: AVAILABLE slots only]
  Reason:       [text field]
  │
  └─ [Confirm] → POST /api/staff/reassign
                    │
                    ├─ Validates: newSlot.status == AVAILABLE
                    │             patient.status == ACTIVE
                    │
                    ├─ Releases current slot (→ AVAILABLE)
                    ├─ Reserves new slot (→ RESERVED, patient_id = X)
                    ├─ Updates Selection record
                    ├─ Logs REASSIGN audit entry
                    │
                    └─ Returns confirmation → Refresh dashboard
```

## Release Flow

```
STAFF selects Appointment → clicks [Release]
  │
  ▼
Release Dialog:
  Appointment: Patient X, Thursday 10:30 AM
  Reason:      [text field]
  │
  └─ [Confirm] → POST /api/staff/release
                    │
                    ├─ Validates: appointment.status in [CONFIRMED]
                    │
                    ├─ Transitions slot → AVAILABLE
                    ├─ Marks appointment → CANCELLED
                    ├─ Updates Selection record
                    ├─ Logs RELEASE audit entry
                    │
                    └─ Returns confirmation → Refresh dashboard
```

## Thymeleaf Template Structure

```
src/main/resources/templates/staff/
├── dashboard.html              # Main dashboard (Weekly Selection + Occupancy + Slot grid)
├── occupancy.html              # Detailed occupancy view with Category breakdown
├── patient-detail.html         # Single Patient view (appointments, categories, actions)
├── patient-list.html           # Searchable/filterable Patient table
├── audit-log.html              # Audit trail viewer with date range + action type filters
├── weekly-selection-result.html # Ranking table (selected + waitlisted)
├── reports.html                # Export reports (CSV download)
└── fragments/
    ├── header.html             # Common header with navigation
    ├── occupancy-panel.html    # Reusable occupancy widget
    └── slot-grid.html          # Reusable slot grid widget
```

## Mark Appointment COMPLETED Flow

```
STAFF opens Patient detail → clicks [Mark Completed] on an Appointment
  │
  ▼
POST /api/staff/appointments/{appointmentId}/complete
  Body: { "statusAtArrival": "PRESENT", "completedAt": "...", "notesFromConsultation": "..." }
  │
  ├─ Validates: appointment.status == CONFIRMED
  │             appointment.slot.dateTime ≤ now
  │
  ├─ appointment.status → COMPLETED
  ├─ patient.lastConsultationDate → today
  ├─ patient.targetDate → recalculated per Category rules
  ├─ Logs COMPLETED audit entry
  │
  └─ Returns updated Patient + Appointment → Refresh patient detail view
```
