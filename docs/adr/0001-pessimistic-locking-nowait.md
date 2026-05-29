# Pessimistic Locking with NOWAIT for Slot Booking

Slot booking uses `SELECT ... FOR UPDATE NOWAIT` (pessimistic write lock, fail-fast) instead of optimistic locking or `FOR UPDATE` with wait. This prevents double-booking under concurrent Patient and Weekly Selection access without blocking other transactions.

**Context**: The system has two concurrent writers for any Slot — the Weekly Selection (claiming 40 BATCH Slots atomically every Monday) and individual Patients (booking one Slot at a time, any day). Without locks, two Patients could simultaneously read `status = AVAILABLE` and both create an Appointment for the same Slot. Optimistic locking (version column) would work for Patient-vs-Patient races but would force the Weekly Selection to retry its entire 40-slot allocation on a single version conflict — unacceptable at scale.

**Decision**: Use `SELECT ... FOR UPDATE NOWAIT` on the Slot row at the start of any booking or reservation transaction. `NOWAIT` means: if the row is already locked, fail immediately with a clear error instead of queueing. The caller (Patient UI or Weekly Selection) handles the failure gracefully — Patients see "Slot temporarily locked, try another," and the Weekly Selection rolls back atomically.

**Rejected alternative — `FOR UPDATE` (with wait)**: Would cause the Weekly Selection to block for up to 30 seconds if a Patient is booking, and vice versa. Worse, it hides contention — the system feels slow instead of clearly failing.

**Rejected alternative — Optimistic locking (version column)**: Clean for single-Slot booking, but the Weekly Selection locks 40 rows at once. A version conflict on Slot #40 after processing 39 would force a full retry of scoring, ranking, and reserving — wasting CPU and risking inconsistent snapshots.

**Consequences**: Every booking endpoint and the Weekly Selection must handle `LockTimeoutException` (or equivalent) explicitly. PostgreSQL `lock_timeout` is set to 30 seconds as a safety net. Monitoring must track lock timeout frequency — a spike indicates contention issues or a stuck transaction.
