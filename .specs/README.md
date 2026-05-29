# PRIORIZASUS — TLC Spec-Driven Specification (Complete)

## 📋 Overview

**9 spec files** organized in `.specs/` directory, implementing the PRIORIZASUS appointment scheduling system for ESF clinics. All files are in **English** (ready for grill-with-docs integration).

## 📁 Directory Structure

```
.specs/
├── project/
│   └── PROJECT.md                      # Vision, scope, tech stack, metrics
├── codebase/
│   └── STACK.md                        # Spring Boot 4.0.6, PostgreSQL, conventions
└── features/
    ├── patient-master/
    │   ├── spec.md                     # Patient registration (PM-001–PM-006), categorization, targetDates
    │   └── tasks.md                    # Implementation tasks with dependency graph
    ├── capacity-model/
    │   ├── spec.md                     # Weekly slot creation (CM-001–CM-006), BATCH allocation
    │   ├── design.md                   # Slot creation algorithm, distribution, state machine
    │   └── tasks.md                    # Implementation tasks with dependency graph
    ├── scoring-algorithm/
    │   ├── spec.md                     # Weekly Selection (SA-001–SA-006), scoring, fairness rules
    │   ├── design.md                   # Algorithm pseudocode, lock lifecycle, concurrency
    │   └── tasks.md                    # Implementation tasks with dependency graph
    ├── booking-system/
    │   ├── spec.md                     # Patient booking (BK-001–BK-006), pessimistic lock, validation
    │   ├── design.md                   # State machine, lock flow, concurrency scenarios
    │   └── tasks.md                    # Implementation tasks with dependency graph
    └── staff-dashboard/
        ├── spec.md                     # Staff dashboard (SD-001–SD-005), occupancy, audit, reporting
        ├── design.md                   # Dashboard layout, occupancy color-code, Reassign/Release flow
        └── tasks.md                    # Implementation tasks with dependency graph
```

## 🎯 File Breakdown

| File | Size | Purpose | Key Decisions |
|------|------|---------|---------------|
| **PROJECT.md** | ~900 words | Vision & constraints | 40 slots/week, fairness algorithm, Maven/Spring Boot |
| **STACK.md** | ~1200 words | Tech conventions | PostgreSQL, pessimistic locking (NOWAIT), repository pattern |
| **patient-master/spec.md** | ~650 words | Patient data model | PM-001–PM-006, lifecycle, audit |
| **capacity-model/spec.md** | ~850 words | Slot management | CM-001–CM-006, 40 BATCH, state machine, availability |
| **scoring-algorithm/spec.md** | ~900 words | Selection logic | SA-001–SA-006, Score formula, Weekly Selection |
| **scoring-algorithm/design.md** | ~1600 words | Algorithm deep-dive | Pseudocode, lock lifecycle, concurrency tests, performance |
| **booking-system/spec.md** | ~950 words | Patient booking | BK-001–BK-006, pessimistic lock, validation chain, cancellation |
| **booking-system/design.md** | ~600 words | Booking design | State machine, lock flow, concurrency scenarios |
| **staff-dashboard/spec.md** | ~1100 words | Admin interface | SD-001–SD-005, Weekly Selection control, occupancy, audit, CSV export |
| **staff-dashboard/design.md** | ~500 words | Dashboard design | Layout, occupancy color-code algorithm, Reassign/Release flow |

**Total: ~8,150 words, all scannable, context-efficient**

## 🔑 Key Design Decisions (Implemented)

✅ **Capacity split**: 40 BATCH (Weekly Selection-allocated by score)  
✅ **Concurrency model**: Pessimistic locking (SELECT ... FOR UPDATE NOWAIT) per slot  
✅ **Scoring weights**: PRENATAL(36w)=1000, CHILD(0-30d)=900, CHRONIC=200 + daysOverdue bonus (capped 500 per Category)  
✅ **Database**: PostgreSQL (ACID, production-ready); H2 for dev/test  
✅ **Framework**: Spring Boot 4.0.6 + Thymeleaf + JPA  
✅ **Weekly batch**: Mondays 7 AM, <5 min execution, staffmonitors results  
✅ **Audit trail**: All batch runs, booking events, cancellations logged  

## 🚀 Next Steps

### Option 1: Use grill-with-docs (Matt Pocock)
Feed the **9 files** into grill-with-docs to auto-generate:
- Structured requirements
- Code scaffolding
- Test skeletons
- Documentation

### Option 2: Create tasks.md (TLC Spec-Driven Phase)
Create `.specs/features/{feature}/tasks.md` for each feature with:
- Atomic implementation tasks (What, Where, Tests, Verification)
- Dependency order
- Sub-agent delegation strategy
- Traceability (REQ-001 → Task-001)

### Option 3: Direct Implementation
Pick a feature (recommend starting with **patient-master**), create Java entities + repositories + services + tests following STACK.md conventions.

## 📖 Reading Order

**For project kickoff**:
1. PROJECT.md (vision & scope)
2. STACK.md (tech decisions)
3. Feature specs in order of dependency:
   1. patient-master/spec.md
   2. capacity-model/spec.md
   3. scoring-algorithm/spec.md + design.md
   4. booking-system/spec.md
   5. staff-dashboard/spec.md

**For development**:
- Use STACK.md as reference guide
- Consult feature spec + design when implementing
- Cross-reference requirements IDs (REQ-001, etc.) with acceptance criteria

## ⚠️ Deferred to Phase 2

- Home visit scheduling (referenced but no capacity model)
- No-show penalty system (stub in dashboard)
- Notifications (email/SMS reminders) — Phase 2 feature
- Multi-user auth (currently single clinic user)
- Mobile app

## ✨ Special Notes

- **All files in English** (compatible with grill-with-docs)
- **No implementation code** (specs are language-agnostic, design.md has pseudocode)
- **Requirements traceable**: Each spec has REQ-001, REQ-002, ... references
- **Acceptance criteria explicit**: Checkboxes for verification
- **Concurrency detailed**: Lock strategy, timeout handling, error scenarios covered
- **Business rules preserved**: All Ministry of Health rules (PRENATAL gestational weeks, CHILD milestones, CHRONIC 60-day follow-up) implemented via `targetDate`/`lastConsultationDate`

---

**Created**: May 23, 2026  
**Status**: Synced with grill-with-docs canonical terminology (Decisions #1–#15)  
**Contact**: PRIORIZASUS Team
