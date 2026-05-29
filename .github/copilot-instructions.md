# PRIORIZASUS — Copilot AI Rules

## Mode Behavior

This file defines a **single, unified instruction set** for all Copilot interactions. The Copilot adapts its behavior based on the task context — there are **no separate instruction files** (emulating agents via multiple `.md` files is an anti-pattern in the Copilot ecosystem).

For true agent separation, use **Copilot Code Review** (native GitHub product feature) as the dedicated, real review agent — not an emulated one.

### Plan Mode (Read-Only)
When analyzing specs, planning features, or reviewing requirements:
- Validate spec file existence, REQ-ID uniqueness, cross-references
- Consult `CONTEXT.md` for canonical terminology before proposing any name
- Consult `docs/adr/` for architectural constraints
- **FORBIDDEN**: Editing files, running terminal commands, implementing code
- **Goal**: Ensure the spec layer is complete and consistent before any code is written

### Implement Mode
When writing or modifying code:
- **Annotate every new public method** with `@ReqId("XX-NNN")` linking to the spec requirement
- **Commit message format**: `feat(REQ-ID): description` or `fix(REQ-ID): description` (parseable by evidence-log.yml)
- **Semantic Fix Loop** (max 3 retries):
  1. `edit` — write code with `@ReqId` annotations
  2. `spotless:apply` — auto-format
  3. `test` — run unit + harness tests
  4. `SpecDriftDetectionTest` — verify spec↔code alignment
  5. `semantic validation` — confirm business rules match spec
  6. `fix` — repair any drift or test failures
- If `SpecDriftDetectionTest` fails after 3 retries: **escalate to human** with drift report
- Every commit must be traceable to a spec REQ-ID

### Review Mode (Read-Only, Intent-Oriented)
When reviewing PRs or code changes:
- **NEVER implement code** — review only
- **Intent-Oriented Checklist**:
  1. Which REQ-ID does this change implement?
  2. Does the code satisfy ALL acceptance criteria in the spec?
  3. Do tests cover the spec scenarios (happy path + edge cases)?
  4. Is behavior consistent with `CONTEXT.md` canonical terms and ADRs?
  5. Is there any drift between spec and implementation? (run `SpecDriftDetectionTest`)
  6. Are `@ReqId` annotations present on all new public service methods?
  7. Does the terminology in code match canonical `CONTEXT.md` terms (not "Avoid" terms)?

---

## Spec-Driven Development (Intent Layer)

- **Always read** `.specs/features/{feature}/spec.md` before implementing any feature.
- **Never implement** features without defined acceptance criteria in the relevant spec.
- If a spec is missing, unclear, or incomplete, **stop and ask** for clarification — do not improvise.
- Every implementation task must be traceable to a feature-prefixed REQ-ID in the spec (e.g., `PM-001`, `CM-001`, `SA-001`, `BK-001`, `SD-001`).

## Context Engineering (Docs Layer)

- **Always consult** `CONTEXT.md` for canonical domain terminology before naming any class, method, or variable.
- **Always consult** `docs/adr/` for architectural decisions that constrain implementation (e.g., pessimistc locking, UTC storage, snapshot eligibility).
- **Always read** `.specs/codebase/STACK.md` before writing code — follow all conventions (package structure, repository pattern, transaction boundaries, naming).
- Never introduce a new term without checking if `CONTEXT.md` already defines a canonical one. If you need a new term, propose adding it to `CONTEXT.md`.

## Layered Architecture (Mandatory)

This project follows a strict layered Spring Boot architecture:

```
controller  →  service  →  repository  →  database
     ↑            ↑            ↑
     └── DTOs ────┘            └── Entity
```

### Controller Layer (`..controller..`)
- Handles HTTP requests and responses ONLY.
- Converts between DTOs and domain objects.
- **FORBIDDEN**: Business logic, direct repository access, `@Transactional` annotation.

### Service Layer (`..service..`)
- Contains ALL business logic, validation, and orchestration.
- The ONLY layer allowed to call repositories.
- All public methods must be `@Transactional` (read-only where applicable).
- **FORBIDDEN**: HTTP concerns (request/response objects), direct database access bypassing repositories.

### Repository Layer (`..repository..`)
- Spring Data JPA interfaces extending `JpaRepository`.
- Pessimistic locks (`SELECT ... FOR UPDATE NOWAIT`) must be declared ONLY here, never in services.
- All custom queries via `@Query` annotation.

### Entity Layer (`..entity..`)
- JPA entities with `@Entity` annotation.
- No business logic beyond basic validation annotations.
- All timestamps stored in UTC (`hibernate.jdbc.time_zone=UTC`).

## Locking & Concurrency Rules

- Pessimistic locking via `SELECT ... FOR UPDATE NOWAIT` is the ONLY concurrency mechanism.
- Lock acquisition belongs exclusively in repository methods annotated with `@Lock(LockModeType.PESSIMISTIC_WRITE)` or `@Query("... FOR UPDATE NOWAIT")`.
- Services must handle `PessimisticLockException` / lock timeout gracefully — never swallow silently.

## Timezone Rules (ADR #0003)

- **Storage**: All timestamps in UTC (`timestamp with time zone`).
- **Display**: Convert to `America/Sao_Paulo` at the presentation layer only.
- **Duration calculations**: Use `LocalDate` and `ChronoUnit.DAYS`, never `Duration.between()` for business day counts.

## Phase 1 Scope Boundary (ADR #0004)

Only these features are in scope:
- Patient Master (`patient-master/`)
- Capacity Model (`capacity-model/`)
- Scoring Algorithm (`scoring-algorithm/`)
- Booking System (`booking-system/`)
- Staff Dashboard (`staff-dashboard/`)

Excluded from Phase 1: home visits, no-show penalties, notifications (email/SMS), multi-user auth, mobile app. Do NOT implement excluded features.

## Coding Standards (STACK.md)

- **Naming**: `PatientService` (classes), `getPatientById()` (methods), `SLOT_DURATION_MINUTES` (constants).
- **Visibility**: Public for service/repository methods; private for internal helpers.
- **Logging**: SLF4J — `private static final Logger log = LoggerFactory.getLogger(ClassName.class);`.
- **Null safety**: Use `Optional<T>` for nullable returns; never return raw `null` from public methods.
- **Testing**: JUnit 5; one logical test per `@Test` method; test class named `{Class}Test`.

## Before Writing Any Code

Run this checklist mentally:

1. Have I read the relevant `.specs/features/{feature}/spec.md`?
2. Have I checked `CONTEXT.md` for canonical terminology?
3. Have I checked `docs/adr/` for relevant architectural constraints?
4. Have I read `.specs/codebase/STACK.md` for conventions?
5. Does my code follow controller → service → repository layering?
6. Is business logic ONLY in services?
7. Are pessimistic locks ONLY in repositories?
8. Are timestamps in UTC?
9. Is this feature in Phase 1 scope?

If any answer is NO — fix it before proceeding.
