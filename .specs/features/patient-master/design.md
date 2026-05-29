# Patient Master — Design Document

## Patient Registration Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ PATIENT REGISTRATION                                             │
├─────────────────────────────────────────────────────────────────┤
│ Input:   Name, CPF (11 digits), Birth Date, Phone, Address      │
│ Output:  Patient record with status=ACTIVE, registrationDate    │
├─────────────────────────────────────────────────────────────────┤
│ 1. Validate CPF format (11 digits)                               │
│ 2. Check CPF uniqueness (duplicate → reject with link to edit)  │
│ 3. Validate birth date not in future                             │
│ 4. Warn on invalid phone format (allow save per spec)           │
│ 5. Persist Patient with status=ACTIVE, UTC timestamps           │
│ 6. Log PATIENT_REGISTERED audit entry                           │
│ 7. Redirect to Category Assignment form                         │
└─────────────────────────────────────────────────────────────────┘
```

## Category Assignment Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ CATEGORY ASSIGNMENT                                              │
├─────────────────────────────────────────────────────────────────┤
│ 1. Staff selects Category type: PRENATAL / CHILD / CHRONIC     │
│ 2. Category-specific fields collected:                          │
│    - PRENATAL: ultrasoundDate + gestationalWeeksAtUltrasound    │
│    - CHILD: lastMilestoneCompleted (Day 7, Day 30, Month 2...)  │
│    - CHRONIC: conditionDescription                              │
│ 3. targetDate calculated per Category rules (PM-003):           │
│    - PRENATAL: today + 7/15/30 days based on gestational weeks  │
│    - CHILD: birthDate + daysToNextMilestone                     │
│    - CHRONIC: lastConsultationDate + 60 days                    │
│ 4. Category persisted with FK → Patient                        │
│ 5. Patient eligible for next Weekly Selection                   │
└─────────────────────────────────────────────────────────────────┘
```

## Target Date Calculation per Category

### PRENATAL (Dynamic — recalculated each Weekly Selection)

```
currentWeeks = gestationalWeeksAtUltrasound + (today - ultrasoundDate).days / 7.0

if currentWeeks < 28:
    targetDate = today + 30 days
elif currentWeeks < 36:
    targetDate = today + 15 days
else:  # 36+ weeks
    targetDate = today + 7 days
```

### CHILD (Milestone-based — Ministry of Health table)

```
nextMilestone = getNextMilestone(lastMilestoneCompleted)

MILESTONE TABLE:
  DAY_7    → 7 days from birth
  DAY_30   → 30 days from birth
  MONTH_2  → 60 days from birth
  MONTH_4  → 120 days from birth
  MONTH_6  → 180 days from birth
  MONTH_9  → 270 days from birth
  MONTH_12 → 365 days from birth
  MONTH_18 → 547 days from birth
  MONTH_24 → 730 days from birth
  ANNUAL   → next birthday (dynamic, age 2+)

targetDate = birthDate + daysToMilestone(nextMilestone)
```

### CHRONIC (Fixed interval)

```
targetDate = lastConsultationDate + 60 days
```

## Patient Status Lifecycle

```
                    ┌──────────┐
                    │  ACTIVE  │◄────────── Staff reactivates
                    └────┬─────┘
                         │
            ┌────────────┼────────────┐
            │            │            │
            ▼            ▼            │
      ┌──────────┐ ┌──────────┐      │
      │INACTIVE  │ │SUSPENDED │      │
      │(terminal)│ │(temporary)│─────┘
      └──────────┘ └──────────┘

Transitions logged: staff user + timestamp + reason
Only ACTIVE Patients appear in Weekly Selection queries
```

## Data Integrity Rules

| Rule | Enforcement |
|------|-------------|
| CPF unique per system | `@Column(unique = true)` + DB constraint |
| Birth date not future | `@Past` validation |
| INACTIVE Patients retained for audit | Status filter on all selection queries |
| All timestamps in UTC | `Instant` type + `hibernate.jdbc.time_zone=UTC` |
| `registrationDate` uses clinic zone | `LocalDate.now(clinicZone)` |

## Entity Relationships

```
Patient ──1:N──> Category
  │                │
  │                ├── type: PRENATAL/CHILD/CHRONIC
  │                ├── targetDate (calculated per clinical rules)
  │                ├── ultrasoundDate + gestationalWeeksAtUltrasound (PRENATAL)
  │                ├── lastMilestoneCompleted (CHILD)
  │                └── conditionDescription (CHRONIC)
  │
  ├── status: ACTIVE/INACTIVE/SUSPENDED
  ├── lastConsultationDate (updated on COMPLETED)
  └── registrationDate (set on @PrePersist)
```
