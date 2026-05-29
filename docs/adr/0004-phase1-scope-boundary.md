# Phase 1 Scope Boundary

Phase 1 (MVP) explicitly excludes: home visit scheduling, no-show penalties, and a mobile app. These are deferred to Phase 2+, not forgotten.

> **Updated May 24, 2026**: Authentication (single ADMIN role) and transactional email (booking links) have been promoted to Phase 1 scope per user requirement. See Consequences below.

**Context**: The core value proposition is the fairness algorithm — replacing the 4 AM queue with algorithmic prioritization. Everything else is secondary. Expanding scope before the algorithm is validated in production risks building features that may need to change once real occupancy and waitlist data exists.

**Decision**: Phase 1 delivers five features (Patient Master, Capacity Model, Scoring Algorithm, Booking System, Staff Dashboard) with basic admin authentication and transactional email for booking links. The explicit exclusions are:

| Excluded Feature | Why Deferred | When |
|-----------------|-------------|------|
| Home visit scheduling | No capacity model defined; separate workflow | Phase 2 |
| No-show penalty system | Needs ≥3 months of no-show data to calibrate | Phase 2 |
| Multi-user / multi-role authentication | Single ADMIN role sufficient; role-based access (admin, staff, patient) deferred | Phase 2 |
| Mobile app | Web portal is responsive; native app adds platform overhead | Phase 3 |

**Rejected alternative — Defer auth to Phase 2**: The Staff Dashboard controls the Weekly Selection and patient management — leaving it open to anyone with the URL is a governance risk even in a single-clinic deployment. A simple username/password (Spring Security, in-memory, single ADMIN role) adds minimal complexity (~2 classes) and protects the control plane.

**Rejected alternative — Defer email to Phase 2**: The reformed booking flow (tokenized links via email) is central to the patient experience. Without email, patients have no way to receive their unique booking link. Fallback: in dev/test, the link is logged to console.

**Consequences**: The system now has Spring Security with one in-memory ADMIN user. The `staff_user` field in audit logs reflects the authenticated admin username (no longer hardcoded). Email sending uses Spring Mail with SMTP config — if credentials are not provided, links are logged to console as a development fallback.
