# PRIORIZASUS — Project Vision & Scope

## Vision

**Build a fair, data-driven Appointment booking system for ESF (Family Health Strategy) clinics** to solve chronic underallocation of medical appointments. Currently, 2,500 Patients compete for 40 weekly Slots with no systematic prioritization, causing vulnerable populations (pregnant women, infants, chronic disease patients) to be squeezed out by walk-ins and repeat-visitors.

## Problem Statement

- **Queues at 4 AM**: Patients line up overnight to secure an appointment
- **No justice**: High-risk patients (pregnant at 37+ weeks, infants at critical milestones) lose slots to lower-priority walk-ins
- **Wasted capacity**: Slots allocated to no-shows; no system to detect and reuse them
- **Manual bottleneck**: Clinic staff manage everything by hand, no audit trail, no data

## Solution Approach

**Algorithmic Weekly Selection** that respects clinical guidelines (Ministry of Health rules for prenatal, puericulture, chronic disease management). Each week:
1. System scores all Patients by clinical urgency + daysOverdue their `targetDate`
2. Selects top 40 candidates into BATCH Slots
3. Patients book from their allocated Slots, no more queuing
4. Staff review, Reassign or Release if needed, monitor occupancy

## Scope — MVP (Phase 1)

### In-Scope
- **Patient master data**: Register, categorize (PRENATAL/CHILD/CHRONIC), track `targetDate` and `lastConsultationDate`
- **Capacity model**: Define weekly schedule (40 Slots = 30-min Appointments, 5 days, 40 BATCH)
- **Scoring algorithm**: Weekly Selection of eligible Patients based on priority + daysOverdue
- **Booking interface**: Eligible Patients see open Slots, book online
- **Staff dashboard**: Clinic staff run Weekly Selection, Reassign/Release bookings, view occupancy

### Out-of-Scope (Phase 2+)
- Home visit scheduling (referenced but not modeled)
- No-show penalty system
- Notifications (email/SMS reminders)
- Multi-clinic federation
- LDAP/SSO integration
- Mobile app

## Technical Stack

| Layer | Choice | Rationale |
|-------|--------|-----------|
| **Language** | Java 22 | Existing Spring Boot project |
| **Framework** | Spring Boot 4.0.6 | MVC, Data JPA, scheduling |
| **UI** | Thymeleaf templates | Server-side rendering, no SPA overhead for MVP |
| **Database** | PostgreSQL | ACID, robust, production-grade; H2 for dev/test |
| **Concurrency** | Pessimistic locking | Prevent double-booking during high contention |
| **Auth** | Hardcoded single user — no authentication system (Phase 1) | Expand to role-based multi-user auth in Phase 2 |

## Key Metrics & Success Criteria

| Metric | Target |
|--------|--------|
| **Slot utilization** | ≥85% (40 Slots/week booked or justifiably empty) |
| **High-risk scheduling** | 100% of PRENATAL (36+w) + critical CHILD booked each cycle |
| **Chronic disease coverage** | ≥80% of eligible CHRONIC Patients booked per cycle |
| **Booking latency** | <500ms for Slot lock + confirmation |
| **System uptime** | 99.5% (clinic hours only) |
| **No double-books** | Zero violations (pessimistic lock = guarantee) |

## Constraints

- **Fixed 40-slot capacity**: Cannot increase, must optimize allocation
- **30-min Slots**: Clinical requirement, non-negotiable
- **Weekly Selection**: Staff run selection once per Week (Monday 7 AM)
- **Clinic hours only**: 8 AM – 5 PM, Monday–Friday
- **Low-latency**: Booking confirmation must be <1 second for Patient UX
- **Audit trail**: All Appointments + Overrides (Reassign/Release) logged with timestamp + staff user

## Non-Functional Requirements

- **Resilience**: Appointment data never lost (PostgreSQL with backups)
- **Concurrency**: Handle 10+ concurrent booking attempts without conflicts
- **Usability**: Staff Weekly Selection process < 5 minutes; Patient booking < 2 min
- **Maintainability**: Clear separation of concerns (services, repositories, entities)
- **Testing**: ≥75% unit test coverage; integration tests for Weekly Selection algorithm

## Roadmap

**Phase 1 (MVP)**: Patient master, capacity, scoring, booking, staff dashboard  
**Phase 2**: Home visits, no-show penalties, multi-user auth  
**Phase 3**: Notifications, mobile app, advanced reporting

---

**Status**: Ready for design → Proceed to feature specification  
**Updated**: May 23, 2026 — Synced with grill-with-docs canonical terminology
