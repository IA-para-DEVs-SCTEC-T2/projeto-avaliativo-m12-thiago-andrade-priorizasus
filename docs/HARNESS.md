# PRIORIZASUS — Development Harness

The development harness is NOT a single folder — it is a distributed system across four layers:

| Layer | Location | Purpose |
|-------|----------|---------|
| **Intent Layer** | `.specs/` | Feature specifications, acceptance criteria, business rules (WHAT to build) |
| **Docs Layer** | `docs/`, `CONTEXT.md` | Architecture decisions (ADRs), domain glossary, technical constraints (HOW to design) |
| **AI Rules Layer** | `.github/copilot-instructions.md` | Copilot Agent Mode execution rules — layered architecture, locking, timezone, scope |
| **Execution Layer** | `.github/workflows/` | CI/CD pipelines (ai-plan, ai-build, ai-review) — automated validation + guard enforcement |

## Execution Flow

```
SPEC → CONTEXT → PLAN (ai-plan.yml) → IMPLEMENT (@ReqId + ai-build.yml) → SPEC DRIFT CHECK (spec-drift-check.yml) → INTENT REVIEW (ai-review.yml) → EVIDENCE LOG (evidence-log.yml) → APPROVAL GATE (ai-pipeline.yml) → MERGE
```

## Copilot Code Review

The project uses **Copilot Code Review** (native GitHub product feature) as the dedicated, real review agent — NOT an emulated agent via separate instruction files. This provides true agent separation at the product level.

To enable: GitHub Repository → Settings → Code Review → Copilot → Enable.

The Review Mode in `.github/copilot-instructions.md` provides the intent-oriented checklist that Copilot Code Review uses: REQ-ID traceability, acceptance criteria coverage, terminology compliance, and spec drift detection.

## CI/CD Pipelines

| Pipeline | Trigger | What it validates |
|----------|---------|-------------------|
| `ai-plan.yml` | PRs changing `.specs/`, `docs/`, `CONTEXT.md` | Spec file existence, REQ-ID uniqueness, cross-references, ADR naming, required sections |
| `ai-build.yml` | Every push & PR | Spotless format, compilation, all tests + JaCoCo coverage |
| `ai-review.yml` | PRs to `main` | ArchUnit layered architecture rules, package structure, controller guardrail scan, REQ-ID traceability, intent-compliance, doc-coverage |
| `spec-drift-check.yml` | PRs changing Java OR spec files | Spec↔code semantic alignment (SpecDriftDetectionTest), business rule consistency |
| `evidence-log.yml` | Merge to `main` | Extracts REQ-IDs from commits, registers approval evidence in TRACEABILITY.md, generates approval-evidence.json artifact |
| `ai-pipeline.yml` | `workflow_dispatch` (manual) | Unified end-to-end pipeline: Plan → Build → Spec Drift → Intent Review → Evidence → Approval Gate |

## Guardrails (Enforced by ArchUnit + CI)

- Business logic forbidden in controllers — must be in services
- `@Transactional` forbidden on controllers — only in services
- Controllers must not access repositories directly
- Pessimistic locks (`FOR UPDATE NOWAIT`) only in repositories
- All timestamps in UTC; display in `America/Sao_Paulo`
- Every feature must have a spec before implementation
- Phase 1 scope: patient-master, capacity-model, scoring-algorithm, booking-system, staff-dashboard

## Semantic Fix Loop Protocol

When the Copilot (Implement Mode) writes code, it follows a semantic validation loop — not just format+test:

```
1. edit       — Write code with @ReqId("XX-NNN") annotations
2. annotate   — Every new public method carries @ReqId linking to spec
3. apply      — spotless:apply (auto-format)
4. test       — Run unit + harness tests
5. drift-check — SpecDriftDetectionTest verifies spec↔code alignment
6. semantic   — Confirm business rules match spec (not just code compiles)
7. fix        — Repair any drift or test failures (max 3 retries)
```

If `SpecDriftDetectionTest` fails after 3 retries → **escalate to human** with a drift report showing which REQ-IDs have no implementation, which code has no REQ-ID, and which business rules are contradictory.

### What SpecDriftDetectionTest validates

| Check | Description |
|-------|-------------|
| **REQ-ID → Code** | Every REQ-ID in specs has at least one `@ReqId` annotation in Java code |
| **Code → REQ-ID** | Public service methods have `@ReqId` annotations (no orphan code) |
| **Semantic Rules** | Critical business rules (FOR UPDATE NOWAIT, UTC timezone) are reflected in code |
| **Drift Detection** | Detects when code contradicts spec (e.g., `priority = normal` when spec says "maximum priority") |

## Test Harness Classes

### `SpecConsistencyTest` (Harness)
Validates spec-to-docs integrity:
- All required spec files exist
- `CONTEXT.md` exists and is comprehensive
- REQ-IDs are unique across all features
- Cross-references between spec files are valid
- ADR files follow `NNNN-name.md` convention
- ADRs contain required sections (Context, Decision, Consequences)
- Feature specs contain required sections (Overview, Requirements, Acceptance Criteria)

### `ArchitectureTest` (ArchUnit)
Enforces layered Spring Boot architecture:
- Controllers, services, repositories, entities in correct packages
- Controllers must not access repositories directly
- Services must not depend on controllers
- No circular package dependencies
- `@Transactional` forbidden on controllers
- Naming conventions enforced
