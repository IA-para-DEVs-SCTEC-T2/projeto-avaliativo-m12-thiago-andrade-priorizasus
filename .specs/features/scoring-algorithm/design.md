# Scoring Algorithm — Design Document

## Algorithmic Overview

```
┌─────────────────────────────────────────────────────────────────┐
│ WEEKLY SELECTION ALGORITHM (Monday 7 AM)                        │
├─────────────────────────────────────────────────────────────────┤
│ 1. FETCH: All ACTIVE Patients with assigned Categories          │
│ 2. FILTER: Snapshot eligibility (status, Categories, timing)    │
│ 3. SCORE: Calculate Score = SUM(weight_i) + SUM(bonus_i)        │
│           per Category i, with daysOverdue capped at 500 each   │
│ 4. RANK: Sort by Score DESC; tie-break by targetDate, then ID   │
│ 5. SELECT: Take top 40 Patients                                 │
│ 6. LOCK: Acquire pessimistic locks on 40 BATCH Slots            │
│ 7. RESERVE: Assign each Patient to a Slot (1:1 mapping)         │
│ 8. COMMIT: Release locks, confirm Reservations                  │
│ 9. LOG: Audit trail + staff notification                        │
└─────────────────────────────────────────────────────────────────┘
```

## Pseudocode

```python
def weekly_selection():
    # Step 1-2: Fetch & Filter (snapshot at Monday 7 AM)
    all_patients = db.query_all_active_patients()
    eligible = [p for p in all_patients if is_eligible(p)]
    
    # Step 3: Score (per-Category weights + per-Category overdue bonuses)
    scored = []
    for patient in eligible:
        total_weight = 0
        total_bonus = 0
        for category in patient.active_categories:
            total_weight += get_category_weight(category, patient)
            days_overdue = max(0, days_between(category.target_date, today))
            bonus = min(days_overdue * 10, 500)  # capped per Category
            total_bonus += bonus
        score = total_weight + total_bonus
        # For tie-breaking: track earliest target_date across all categories
        earliest_target = min(c.target_date for c in patient.active_categories)
        scored.append((patient.id, score, earliest_target, patient.registration_date))
    
    # Step 4: Rank
    scored.sort(
        key=lambda x: (-x[1], x[2], x[3])  # descending score, asc targetDate, asc registration
    )
    
    # Step 5: Select top 40
    selected = scored[:40]
    selected_ids = [s[0] for s in selected]
    
    # Step 6-7: Lock & Reserve
    transaction.begin()
    try:
        batch_slots = db.lock_batch_slots(40)  # FOR UPDATE NOWAIT
        for i, patient_id in enumerate(selected_ids):
            slot = batch_slots[i]
            slot.patient_id = patient_id
            slot.status = "RESERVED"
            db.save(slot)
        
        # Step 8: Commit
        transaction.commit()
    except LockTimeoutException:
        transaction.rollback()
        log_error("Weekly Selection failed: slot lock timeout")
        return {"status": "FAILED", "reason": "Concurrency conflict"}
    
    # Step 9: Log
    log_weekly_selection_result(selected_ids, scored)
    return {"status": "SUCCESS", "selected_count": 40, "ranking": scored}

def is_eligible(patient):
    return (
        patient.status == "ACTIVE"
        and len(patient.active_categories) > 0
        and not patient.has_booked_appointment_this_week()  # BOOKED only, not RESERVED
        and days_between(patient.last_consultation_date, today) >= 7
    )

def get_category_weight(category, patient):
    if category.type == "PRENATAL":
        weeks = patient.calculate_current_gestational_weeks()
        if weeks >= 36: return 1000
        elif weeks >= 28: return 500
        else: return 300
    elif category.type == "CHILD":
        next_milestone = get_next_milestone(patient.last_milestone_completed)
        if next_milestone in [DAY_7, DAY_30]: return 900
        elif next_milestone in [M2, M4, M6, M9, M12]: return 700
        else: return 400  # M18, M24, ANNUAL
    elif category.type == "CHRONIC":
        return 200
    return 0
```

## Data Structures

### Slot Lock Management

```
┌─────────────────────────────────────┐
│ WeeklySelection                     │
├─────────────────────────────────────┤
│ id: Long (PK)                       │
│ week_start: LocalDate               │
│ executed_at: Instant (UTC)          │
│ total_selected: Integer             │
│ status: PENDING/COMPLETED/FAILED    │
│ error_message: String (nullable)    │
│ staff_user: String                  │
└─────────────────────────────────────┘
           ↓ 1:N
┌─────────────────────────────────────┐
│ Selection                            │
├─────────────────────────────────────┤
│ id: Long (PK)                       │
│ weekly_selection_id: Long (FK)      │
│ patient_id: Long (FK)               │
│ score: Integer                      │
│ rank: Integer                       │
│ slot_id: Long (FK, nullable)        │
│ status: SELECTED/BOOKED/RELEASED    │
└─────────────────────────────────────┘
           ↓ N:1
┌─────────────────────────────────────┐
│ Slot                                │
├─────────────────────────────────────┤
│ id: Long (PK)                       │
│ week_start: LocalDate (FK)          │
│ slot_datetime: Instant (UTC)        │
│ type: BATCH                          │
│ status: AVAILABLE/RESERVED/BOOKED/  │
│         CANCELLED/EXPIRED           │
│ patient_id: Long (FK, nullable)     │
└─────────────────────────────────────┘
```

**Note**: Pessimistic lock is held at the database level (`FOR UPDATE NOWAIT`). No application-level `locked_until` field needed — PostgreSQL handles lock release on transaction commit/rollback or after `lock_timeout` (30s).

### Score Calculation per Category

```
Category                        Weight  Overdue Bonus (per Category)   Notes
────────────────────────────────────────────────────────────────────────────
PRENATAL (36+ weeks gest)       1000    daysOverdue × 10 (cap:500)     Highest risk
PRENATAL (28–36 weeks gest)     500     daysOverdue × 10 (cap:500)     
PRENATAL (<28 weeks gest)       300     daysOverdue × 10 (cap:500)     
CHILD (Day 7, 30 milestones)    900     daysOverdue × 10 (cap:500)     Newborn critical window
CHILD (Month 2-12 milestones)   700     daysOverdue × 10 (cap:500)     Puericulture active
CHILD (18m, 24m, annual)        400     daysOverdue × 10 (cap:500)     Development checks
CHRONIC (all)                   200     daysOverdue × 10 (cap:500)     Routine 60-day checkup

Multi-Category: Score = SUM(weight_i) + SUM(bonus_i) for all active Categories
```

## Concurrency & Lock Strategy

### Pessimistic Locking Flow

```
Timeline: Monday 7 AM Weekly Selection
├─ 7:00:00 AM: Weekly Selection starts, transaction opens
├─ 7:00:05 AM: Query eligible Patients (snapshot; no locks yet)
├─ 7:00:10 AM: Score & rank Patients (no locks yet)
├─ 7:00:15 AM: BEGIN LOCK ACQUISITION
│  ├─ SET lock_timeout = '30s';
│  ├─ SELECT * FROM Slot WHERE type='BATCH' FOR UPDATE NOWAIT
│  │  (Acquire exclusive lock on all 40 BATCH Slots)
│  └─ Lock acquired successfully
├─ 7:00:20 AM: RESERVE SLOTS
│  ├─ For i=1 to 40:
│  │    slots[i].patient_id = selected_patients[i].id
│  │    slots[i].status = 'RESERVED'
│  │    db.update(slots[i])
├─ 7:00:25 AM: COMMIT TRANSACTION
│  └─ Locks released on commit
├─ 7:00:30 AM: Weekly Selection complete
└─ 7:01:00 AM: Staff dashboard updated
```

### Concurrent Booking Conflict Prevention

If a **Patient tries to book a BATCH Slot while Weekly Selection is running**:

```
Patient Booking Timeline (concurrent):
├─ 7:00:12 AM: Patient A clicks "Book Slot 5"
├─ 7:00:13 AM: BookingService.lockSlotForBooking(slot=5)
│  └─ SELECT * FROM Slot WHERE id=5 FOR UPDATE NOWAIT
│  └─ ⚠️ TIMEOUT: Slot already locked by Weekly Selection
├─ 7:00:14 AM: LockTimeoutException thrown
├─ 7:00:15 AM: HTTP 409 Conflict returned to Patient UI
│  └─ Message: "Slot temporarily locked, try again in 30 seconds"
└─ Patient retries at 7:01:00 AM (after Weekly Selection completes)
```

### Lock Configuration (Spring + PostgreSQL)

```java
@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.type = 'BATCH' 
            AND s.status = 'AVAILABLE'")
    List<Slot> lockBatchSlotsForUpdate();
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "javax.persistence.lock.timeout", 
                            value = "0")})  // NOWAIT: fail fast
    Optional<Slot> lockSlotForUpdate(@Param("id") Long slotId);
}
```

**Lock timeout**: `SET lock_timeout = '30s'` at session level; 0 for `@QueryHints` = NOWAIT (fail immediately if locked)  
**Transaction isolation**: READ_COMMITTED (default); sufficient for pessimistic locks

## Error Handling

```
┌──────────────────────────────────────────┐
│ Weekly Selection Error Scenarios         │
├──────────────────────────────────────────┤
│                                          │
│ 1. Eligible Patients < 35                │
│    → Select all eligible                 │
│    → Some BATCH Slots remain AVAILABLE   │
│    → Not an error; expected in lean Week │
│                                          │
│ 2. Lock timeout (Slots already locked)   │
│    → Weekly Selection FAILS; rollback    │
│    → Staff notified: "Retry at 7:30 AM"  │
│    → Previous Reservations remain valid  │
│                                          │
│ 3. Database error during reserve         │
│    → Transaction rolled back             │
│    → All Slots released                  │
│    → Weekly Selection marked FAILED      │
│                                          │
│ 4. Staff cancels running Weekly Selection│
│    → SIGTERM received                    │
│    → Rollback in progress                │
│    → All locks released; Slots back FREE │
│                                          │
│ 5. PostgreSQL connection dropped mid-lock│
│    → Locks auto-released after 30s       │
│    → Clean retry possible                │
│                                          │
└──────────────────────────────────────────┘
```

## Audit Trail

Every Weekly Selection execution logged:

```sql
INSERT INTO WeeklySelection (week_start, executed_at, total_selected, status)
VALUES ('2026-05-27', '2026-05-27T10:00:00Z', 35, 'COMPLETED');

INSERT INTO Selection (weekly_selection_id, patient_id, score, rank, slot_id, status)
VALUES 
  (1, 1001, 1100, 1, 5001, 'SELECTED'),
  (1, 1002, 900,  2, 5002, 'SELECTED'),
  ...
  (1, 1035, 450,  35, 5040, 'SELECTED');

-- Non-selected Patients (for transparency):
SELECT * FROM Selection 
WHERE weekly_selection_id = 1 AND rank > 35 
ORDER BY rank ASC;
```

## Performance Considerations

- **Query eligible Patients**: Indexed on (status, active_categories) → <100ms for 2500 Patients
- **Score calculation**: In-memory (no DB) → <50ms for 75 eligible
- **Sorting**: O(n log n) → <20ms
- **Lock acquisition**: Depends on concurrent bookings; pessimistic lock holds <100ms typical, <500ms under contention
- **Total Weekly Selection time**: Target <5 minutes for staff to monitor

## Testing Strategy

| Test Case | Scenario | Expected Result |
|-----------|----------|-----------------|
| **Normal case** | 75 eligible, select 40 | Top 40 by Score selected; locks acquired; audit logged |
| **Tie-breaking** | 2 Patients same Score | Earliest `targetDate` wins; if still tied, earliest registration wins |
| **Multi-Category scoring** | PRENATAL + CHRONIC Patient | Both weights summed; per-Category `daysOverdue` bonuses summed independently |
| **Few eligible** | 20 eligible, select 20 | All 20 selected; 20 BATCH Slots remain AVAILABLE |
| **Concurrent booking** | Weekly Selection locks + Patient books simultaneously | Patient gets 409 Conflict; Weekly Selection succeeds |
| **Lock timeout** | Too many concurrent bookings | Weekly Selection FAILS with error; staff retries |
| **Data change mid-Weekly Selection** | Patient status changes during selection | Snapshot from Monday 7 AM used; change applies next Weekly Selection |
| **Re-run Weekly Selection** | Staff triggers selection twice | Second run releases prev Reservations; recalculates with current snapshot |
| **daysOverdue cap** | Patient 60+ days overdue in one Category | Bonus capped at 500 for that Category; other Categories calculated independently |

--- — Synced with grill-with-docs (Decisions #1, #5, #6, #9, #12)

**Last Updated**: May 23, 2026
