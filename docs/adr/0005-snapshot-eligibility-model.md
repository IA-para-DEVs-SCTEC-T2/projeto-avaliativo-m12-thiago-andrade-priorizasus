# Snapshot Eligibility Model for Weekly Selection

Patient eligibility for the Weekly Selection is evaluated once — at the moment the Selection runs (Monday 7 AM) — using a snapshot of Patient data. Eligibility is not re-evaluated mid-week, even if a Patient's status or Category changes.

**Context**: The Weekly Selection reserves BATCH Slots for 40 Patients based on their Score at the time of selection. If eligibility were continuously re-evaluated, a Patient who was selected on Monday could lose their Reservation on Wednesday (e.g., because they had a consultation on Tuesday and now have <7 days since `lastConsultationDate`). This would create a confusing UX: "You had a slot, now you don't."

**Decision**: Eligibility is a snapshot at Selection time. Once a Patient receives a Reservation, it is guaranteed until Friday 5 PM (unless staff explicitly Releases it). Mid-week status changes (consultations, Category changes, status changes) only affect the next week's Selection.

**Rejected alternative — Continuous eligibility**: More "accurate" (reflects real-time Patient state) but creates several problems:
- **Fairness**: A Patient could game the system by booking, getting scored, then cancelling — their Score snapshot is "stale" but they keep the slot.
- **Auditability**: "Why was Patient X selected?" becomes non-reproducible — their state at Monday 7 AM may differ from their state when the question is asked on Wednesday.
- **UX volatility**: Patients see Reservations appear and disappear mid-week based on factors outside their control.

**Rejected alternative — Pre-appointment eligibility check**: Check eligibility again at Booking time (when Patient clicks "Confirm"). This would block Patients whose status changed between Monday and their Booking — but the Reservation already consumed a BATCH Slot that cannot be reallocated mid-week (GRILL-DECISIONS #3). A blocked Booking wastes the Slot entirely.

**Consequences**: A Patient who becomes INACTIVE mid-week still holds a Reservation until Friday. Staff must manually Release these Reservations via the dashboard. This is a known operational overhead — Phase 2 should add an automated check: if Patient status becomes INACTIVE, auto-Release all their Reservations and flag for staff review.
