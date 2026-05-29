# Booking System — Design Document

## Booking State Machine

```
                    ┌──────────────────────────────────────┐
                    │           SLOT STATE MACHINE          │
                    └──────────────────────────────────────┘

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
                        └────┬─────┘                               │
                             │                                     │
                    Staff marks                                    │
                    COMPLETED                                      │
                             │                                     │
                             ▼                                     │
                        ┌──────────┐                               │
                        │COMPLETED │  (terminal)                   │
                        └──────────┘                               │
                                                                   │
                        ┌──────────┐                               │
                        │ CANCELLED│◄──────────────────────────────┘
                        └──────────┘  (returns to AVAILABLE)

   Friday 5 PM: RESERVED (unconfirmed) → EXPIRED (terminal)
                        ┌──────────┐
                        │ EXPIRED  │  (terminal, not reusable)
                        └──────────┘
```

## Booking Sequence (Pessimistic Lock Flow)

```
Patient A clicks "Book Slot #5"
│
├─ Step 1: Validation chain (no locks)
│   ├─ Patient exists? ACTIVE? ✓
│   ├─ Already BOOKED this Week? (max 1) ✓
│   ├─ Slot exists? Not EXPIRED? ✓
│   ├─ Slot = RESERVED for Patient A? ✓
│   └─ Slot time not in past? ✓
│
├─ Step 2: Acquire Pessimistic Lock
│   │  BEGIN TRANSACTION
│   │  SET lock_timeout = '30s'
│   │  SELECT * FROM Slot WHERE id=5 FOR UPDATE NOWAIT
│   │
│   ├─ SUCCESS: Lock acquired → proceed
│   └─ FAILURE: Lock held by another tx → 409 Conflict
│       │  "Slot temporarily locked, try another"
│       └─ ROLLBACK
│
├─ Step 3: Mutate (lock held, <100ms)
│   │  UPDATE Slot SET status='BOOKED', patient_id=A WHERE id=5
│   │  INSERT INTO Appointment (patient_id, slot_id, status='CONFIRMED', ...)
│   │
│   └─ COMMIT TRANSACTION → Lock released
│
└─ Step 4: Response
    └─ HTTP 200: { appointmentId, slotDateTime, status }
```

## Concurrency Scenario: Two Patients, Same Slot

```
Timeline →
Patient A          │──Validate──│──Lock──│──UPDATE──│──COMMIT──│  200 OK
                   │            │        │          │          │
Patient B          │──Validate──│──Lock──│
                                 │  NOWAIT fails!
                                 │  Lock held by A
                                 │
                                 └─ 409 Conflict
                                    "Slot already booked"
```

## Validation Chain Order

The order of validations in `BK-002` is intentional:

| # | Validation | Rationale |
|---|-----------|-----------|
| 1 | Patient exists & ACTIVE | Fail fast — cheapest check, no DB lock |
| 2 | Patient not BOOKED this Week | Business rule, prevents double-booking |
| 3 | Slot exists & not EXPIRED | Prevents booking deleted/expired slots |
| 4 | Slot RESERVED for this Patient | Core fairness rule — only selected Patients book |
| 5 | Slot time not in past | Can't book historical appointments |

Locks are acquired **only after** all validations pass — minimizing lock duration.

## Endpoint Summary

| Method | Endpoint | Lock? | Description |
|--------|----------|-------|-------------|
| `GET` | `/api/booking/my-available-slots` | No | List slots with visibility labels |
| `GET` | `/api/booking/my-appointments` | No | Patient's current + past appointments |
| `POST` | `/api/booking/reserve` | Yes (NOWAIT) | Book a RESERVED slot |
| `DELETE` | `/api/booking/appointments/{id}` | Yes (NOWAIT) | Cancel appointment, release slot |

## Error Responses

| HTTP Code | Condition | Message |
|-----------|-----------|---------|
| `400` | Validation fails | Specific reason (e.g., "Already booked this week") |
| `403` | Not eligible | "You are not eligible for this slot" |
| `409` | Lock conflict | "Slot temporarily locked, try another" |
| `404` | Slot/Patient not found | "Resource not found" |
