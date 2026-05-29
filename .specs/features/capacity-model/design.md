# Capacity Model — Design Document

## Weekly Slot Creation Algorithm

```
┌─────────────────────────────────────────────────────────────────┐
│ WEEKLY SLOT CREATION (Monday 7 AM)                               │
├─────────────────────────────────────────────────────────────────┤
│ Input:   weekStart (Monday date)                                 │
│ Output:  40 BATCH Slots, all status=AVAILABLE                   │
├─────────────────────────────────────────────────────────────────┤
│ 1. Calculate clinic days: weekStart → weekStart+4 (Mon–Fri)     │
│ 2. For each day:                                                 │
│     - Start time = 08:00 (clinic opens)                         │
│     - End time = 17:00 (clinic closes)                           │
│     - Generate slots: 30 min intervals from start to end        │
│       (max 18 slots/day × 5 days = 90 potential slots)          │
│ 3. Select first 40 slots (fill from Monday morning forward)     │
│ 4. Persist all 40 slots with:                                    │
│     - type = BATCH                                                │
│     - status = AVAILABLE                                         │
│     - datetime in UTC                                            │
│ 5. Weekly Selection (SA-004) runs immediately after              │
│ 6. Log: "40 slots created for week starting {weekStart}"         │
└─────────────────────────────────────────────────────────────────┘
```

## Slot Distribution

```
Week = Monday–Friday, 8 AM – 5 PM

Day       Slots Created    Time Range
──────────────────────────────────────
Monday     8 slots         08:00 – 11:30 (then Weekly Selection fills)
Tuesday    8 slots         08:00 – 11:30
Wednesday  8 slots         08:00 – 11:30
Thursday   8 slots         08:00 – 11:30
Friday     8 slots         08:00 – 11:30
           ───
           40 total BATCH
```

Note: 40 slots fill the morning shift (4h/day × 2 slots/h × 5 days = 40). Afternoon slots (12:00–17:00) are not created in Phase 1.

## Slot State Machine

```
                    ┌──────────┐
                    │AVAILABLE │◄──────────────────────────────┐
                    └────┬─────┘                               │
                         │                                     │
                Weekly Selection                               │
                (SA-004 lock)                                  │
                         │                                     │
                         ▼                                     │
                    ┌──────────┐     Patient/Staff cancels     │
                    │ RESERVED │───────────────────────────────┤
                    └────┬─────┘                               │
                         │                                     │
                Patient confirms                               │
                (BK-003 lock)                                  │
                         │                                     │
                         ▼                                     │
                    ┌──────────┐     Patient/Staff cancels     │
                    │  BOOKED  │───────────────────────────────┤
                    └──────────┘                               │
                                                               │
                    ┌──────────┐                               │
                    │ CANCELLED│◄──────────────────────────────┘
                    └──────────┘  → returns to AVAILABLE
                                                               │
   Friday 5 PM: unconfirmed RESERVED                           │
                    ┌──────────┐                               │
                    │ EXPIRED  │  (terminal, not reusable)     │
                    └──────────┘                               │
```

## Capacity Query Logic

```
GET /api/capacity/week/{weekStart}
  → Returns all 40 Slots for the Week with current status
  → No filtering — staff view of full capacity

GET /api/capacity/available?patientId={id}
  → Returns Slots bookable by this Patient:
    1. Slot.status = RESERVED AND Slot.patient_id = {id}  → "RESERVED_FOR_ME"
    2. All other RESERVED Slots → "RESERVED_FOR_ANOTHER" (🔒)
  → Excludes: BOOKED, EXPIRED slots
  
GET /api/capacity/occupancy?weekStart={date}
  → Returns: BOOKED count, CANCELLED count, EXPIRED count, utilization %
```

## Trigger Lifecycle

```
Monday 6:55 AM ─ Staff logs in, reviews dashboard
Monday 7:00 AM ─ Scheduled task triggers:
                  │
                  ├─ Step 1: Create 40 BATCH Slots (CM-001)
                  │   Duration: <1 second
                  │
                  ├─ Step 2: Weekly Selection runs (SA-004)
                  │   Duration: <30 seconds (typical)
                  │   Atomically reserves 40 slots
                  │
                  └─ Step 3: Staff reviews results
                      Duration: <5 minutes (manual review)

If Weekly Selection fails:
  → Slots remain AVAILABLE
  → Staff notified in dashboard
  → Can re-trigger manually
  → No partial results (all-or-nothing transaction)
```

## Edge Cases

| Case | Handling |
|------|----------|
| **Holiday** | Config: skip day; fewer than 40 slots created |
| **Clinic closed** | Config: skip entire week; no slots |
| **DST transition** | UTC storage handles this — no impact on slot times |
| **Midnight boundary** | Slots created at 7 AM Monday, dated for the coming week |
