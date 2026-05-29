# CONTEXT.md Entry Format

Each glossary entry in `CONTEXT.md` follows this template:

```markdown
**Term Name**:
Definition. Key properties. Purpose in the domain.
_Avoid_: Alt1, Alt2, Alt3
```

## Rules

1. **Bold the canonical term** — this is the ONLY term allowed in code, docs, and specs.
2. **Define it in one paragraph** — what it is, its key attributes, when it's used.
3. **List `_Avoid_` terms** — synonyms or related terms that MUST NOT be used. This prevents ambiguity.
4. **No implementation details** — `CONTEXT.md` is a glossary, not a spec. Don't mention database tables, API endpoints, or code.
5. **Group related terms** under `##` sections (e.g., `## Language > ### Core Entities`).

## Example

```markdown
**Patient**:
A person registered in the clinic's catchment area who may receive medical appointments. A Patient has a status (ACTIVE, INACTIVE, or SUSPENDED) and zero or more Categories.
_Avoid_: Client, User, Enrollee, Beneficiary, Applicant
```
