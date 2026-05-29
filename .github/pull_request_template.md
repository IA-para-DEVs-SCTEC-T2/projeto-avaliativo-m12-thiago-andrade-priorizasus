## PRIORIZASUS — Pull Request

### Intent-Oriented Checklist

Before submitting, confirm:

- [ ] **REQ-ID(s) implemented**: `___` (e.g., `PM-001`, `SA-003`)
- [ ] **Spec read and confirmed**: `___` (feature spec path)
- [ ] **Acceptance criteria covered by tests**: All spec scenarios have corresponding test cases
- [ ] **`SpecDriftDetectionTest` passes**: `mvn test -Dtest="SpecDriftDetectionTest"`
- [ ] **Canonical terminology (CONTEXT.md) respected**: No "Avoid" terms used in code
- [ ] **ADRs consulted**: Relevant architectural decisions reviewed (list: `___`)
- [ ] **`@ReqId` annotations present**: On all new public service methods
- [ ] **Commit messages follow format**: `feat(REQ-ID): description` or `fix(REQ-ID): description`

### Intent Statement

> **This change fulfills requirement(s)**: `___`
>
> **How it satisfies the spec's acceptance criteria**: `___`

### CI Gates

| Gate | Status |
|------|--------|
| ai-plan (spec validation) | ⬜ |
| ai-build (compile + test + spotless) | ⬜ |
| spec-drift-check (SpecDriftDetectionTest) | ⬜ |
| ai-review (architecture + intent-compliance) | ⬜ |

### Human Review Prompts

- **Architecture**: Does this respect layered architecture (controller → service → repository)?
- **Domain**: Does the terminology match CONTEXT.md canonical terms?
- **Intent**: Does this change actually satisfy the REQ-ID it claims to implement?
