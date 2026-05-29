# UTC Storage with Local Display for All Timestamps

All timestamps are stored in UTC in the database and converted to the clinic's local timezone (`America/Sao_Paulo`) for display. Duration calculations use `LocalDate` to avoid daylight saving time (DST) edge cases.

**Context**: The clinic operates in Brazil (BRT, UTC−3), which does not observe DST since 2019. However, the system may be deployed to other regions, and PostgreSQL's `timestamp with time zone` normalizes to UTC by design. Storing local times directly (`timestamp without time zone`) would embed a hidden timezone assumption in every row.

**Decision**: Three rules:
1. **Storage**: `timestamp with time zone` (UTC). Configured via `hibernate.jdbc.time_zone=UTC`.
2. **Display**: Convert to `America/Sao_Paulo` at the presentation layer. Slots show "9:00 AM" (local), not "12:00 PM" (UTC).
3. **Duration calculations**: Use `LocalDate` and `ChronoUnit.DAYS`. `daysOverdue = DAYS.between(targetDate, LocalDate.now(clinicZone))`. This avoids DST-related off-by-one errors that `Duration.between()` can produce across DST transitions.

**Rejected alternative — Local time storage**: Simpler display logic (no conversion), but couples the database to a specific timezone. A clinic in a different region would require data migration. Also, `timestamp without time zone` makes cross-region comparisons ambiguous.

**Rejected alternative — Storing both UTC and local**: Redundant, risks divergence between the two columns, and doubles storage for every timestamp column.

**Consequences**: Every query that compares dates (e.g., "appointments today") must convert the local date boundary to UTC before querying. `BETWEEN` clauses on `appointmentDateTime` need UTC-aware range calculation. This is handled in the repository layer with a `ClinicTimeZone` utility component.
