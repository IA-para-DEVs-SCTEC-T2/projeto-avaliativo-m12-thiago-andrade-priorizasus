# 40 Slots All-BATCH (No WALK_IN Split)

The weekly schedule of 40 Slots is allocated entirely by the Weekly Selection scoring algorithm. All 40 Slots are BATCH Slots — there is no separate WALK_IN category in Phase 1. Every Slot goes to a Patient selected by Score.

**Context**: The clinic serves 2,500 Patients with 40 weekly Slots. An earlier design considered splitting capacity into 35 BATCH (algorithmic) + 5 WALK_IN (first-come-first-serve for acute care). This split was rejected in favor of simplicity: all 40 Slots flow through the same scoring algorithm, ensuring maximum fairness and a single allocation path. The Regras de Negócio document states the canonical rule: "O sistema seleciona os 40 pacientes com maior score."

**Decision**: 40 BATCH / 0 WALK_IN (100% algorithmic). This ensures:
- Maximum fairness: every Slot is allocated by clinical priority, not arrival order
- Simplicity: a single allocation path with no special-casing for Slot types
- The top 40 Patients by Score receive Reservations each Week

**Rejected alternative — 35/5 BATCH/WALK_IN split**: Originally proposed to reserve 5 Slots for acute visits. However, this created two allocation paths (algorithmic vs. first-come-first-serve), added complexity to the Booking module (different validation rules per Slot type), and reduced algorithmic capacity to 35. With 75–100 eligible Patients per Week, 35 BATCH Slots meant 40–65 waitlisted — including potentially high-risk Patients. Running all 40 through scoring ensures the most clinically urgent Patients always get priority.

**Rejected alternative — Configurable split**: Adds complexity (admin UI, validation, edge cases with mid-week changes) for a decision the clinic has no data to tune yet. Phase 2 can revisit whether a separate acute-care allocation path is needed once occupancy data exists.

**Consequences**: All 40 Slots are BATCH type. Unconfirmed BATCH Reservations expire Friday 5 PM (RESERVED → EXPIRED). There is no mid-week fallback or conversion between Slot types — the model is uniform. Acute-care Patients (flu, pain, wound care) must wait for the next Weekly Selection unless they rank within the top 40 by Score. Phase 2 may introduce a dedicated acute-care scoring category or a separate WALK_IN mechanism if occupancy data shows unmet acute demand.
