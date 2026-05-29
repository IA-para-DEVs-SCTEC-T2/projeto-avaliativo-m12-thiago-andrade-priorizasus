# ADR Format

Each Architecture Decision Record (ADR) in `docs/adr/` follows this template:

```markdown
# Title (short, descriptive, imperative mood)

One-sentence summary of the decision.

**Context**: Why this decision is needed. What constraints, requirements, or problems led to this point. Describe the current state and the forces at play.

**Decision**: What we decided. Be specific — include technical details, naming, and boundaries. This is the "what and how."

**Rejected alternative(s)**: What we considered and why we didn't choose it. For each alternative, explain the trade-off that made it unacceptable. This is critical for future readers to understand the reasoning.

**Consequences**: What this means going forward. What becomes easier, what becomes harder, what new constraints are introduced. Be honest about downsides.
```

## Naming Convention

Files are named `NNNN-description.md` where `NNNN` is a zero-padded sequential number (e.g., `0001`, `0002`).

## When to Create an ADR

Only create an ADR when ALL three are true:

1. **Hard to reverse** — the cost of changing your mind later is meaningful
2. **Surprising without context** — a future reader will wonder "why did they do it this way?"
3. **The result of a real trade-off** — there were genuine alternatives and you picked one for specific reasons

If any of the three is missing, skip the ADR — document the decision inline in code comments or the relevant spec's `design.md`.
