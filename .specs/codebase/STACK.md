# PRIORIZASUS — Technology Stack & Conventions

## Framework & Dependencies

```
Java 22 + Spring Boot 4.0.6
├── spring-boot-starter-web           # MVC, embedded Tomcat
├── spring-boot-starter-data-jpa      # Hibernate ORM, repositories
├── spring-boot-starter-thymeleaf     # Server-side templates
├── postgresql (runtime)               # Production database
├── h2 (test scope)                    # In-memory for tests
├── spring-boot-starter-test          # JUnit 5, Mockito
└── spring-boot-maven-plugin          # Build & run
```

## Database

### Production
- **PostgreSQL** (version 13+)
- **Connection pooling**: HikariCP (Spring Boot default)
- **DDL auto**: `spring.jpa.hibernate.ddl-auto=update` (Phase 1); migrations (Flyway) deferred to Phase 2+ when schema stabilizes
- **Transactions**: Read Committed isolation (default); pessimistic locks use SELECT...FOR UPDATE NOWAIT

### Development / Testing
- **H2 in-memory**: Profile `application-test.properties`
- **DDL auto**: `spring.jpa.hibernate.ddl-auto=create-drop` (test profile only)

### Entity Naming
- Table names: `snake_case` (e.g., `patient_appointments`, `weekly_slots`)
- Primary keys: Always `id` (Long, auto-increment)
- Foreign keys: `{entity}_id` (e.g., `patient_id`, `slot_id`)
- Timestamps: `created_at`, `updated_at` (stored in UTC)

## Concurrency & Locking Strategy

**Pessimistic Lock Pattern** (prevent double-booking):

```java
// Repository: @Lock(PESSIMISTIC_WRITE) @QueryHints(@QueryHint(name="jakarta.persistence.lock.timeout", value="30000"))
//              @Query("SELECT s FROM Slot s WHERE s.id = :id")
//              Optional<Slot> lockSlotForUpdate(@Param("id") Long id);
//
// Service: Slot lockedSlot = slotRepository.lockSlotForUpdate(slotId)
//              .orElseThrow(() -> new SlotLockException("Slot not found"));
//          lockedSlot.setStatus(SlotStatus.BOOKED);
//          slotRepository.save(lockedSlot);
```

**Lock Timeout**: 30s (configurable via `@QueryHint`); NOWAIT (fail fast if slot already locked; client retries)  
**Lock Scope**: Per-slot (`lockSlotForUpdate`) or all 40 BATCH Slots (`lockBatchSlotsForUpdate`), held only during booking confirmation (< 100ms)  
**Rollback**: If booking fails, lock releases automatically on transaction end

## Code Organization

```
src/main/java/com/priorizasus/priorizasus/
├── annotation/           # Custom annotations
│   └── ReqId.java        # @ReqId("XX-NNN") for spec traceability
├── entity/               # JPA entities + enums
│   ├── Patient.java / PatientStatus.java
│   ├── Category.java / CategoryType.java / ChildMilestone.java
│   ├── Slot.java / SlotStatus.java / SlotType.java
│   ├── Appointment.java / AppointmentStatus.java
│   ├── WeeklySelection.java / WeeklySelectionStatus.java
│   ├── Selection.java / SelectionStatus.java
│   ├── AuditLog.java / AuditActionType.java
│   └── BookingToken.java
├── repository/           # Spring Data repositories
│   ├── PatientRepository.java
│   ├── CategoryRepository.java
│   ├── SlotRepository.java
│   ├── AppointmentRepository.java
│   ├── AuditLogRepository.java
│   └── BookingTokenRepository.java
├── service/              # Business logic
│   ├── PatientService.java
│   ├── CapacityService.java
│   ├── ScoringService.java
│   ├── BookingService.java
│   ├── AppointmentService.java
│   ├── AuditLogService.java
│   └── EmailService.java (Phase 2 stub)
├── controller/           # HTTP endpoints (MVC + REST)
│   ├── HomeController.java
│   ├── PatientController.java
│   ├── BookingController.java
│   ├── CapacityController.java
│   ├── ScoringController.java
│   ├── StaffController.java / StaffApiController.java
│   └── AuthController.java (Phase 2 stub)
├── dto/                  # Data transfer objects (records)
│   ├── PatientDTO.java
│   ├── SlotDTO.java
│   ├── AppointmentDTO.java
│   ├── OccupancyDTO.java / CategoryOccupancyDTO.java
│   ├── SelectionResultDTO.java / ScoredPatientDTO.java / CategoryScoreDTO.java
│   ├── ReassignRequestDTO.java / ReleaseRequestDTO.java
│   └── CompleteAppointmentRequestDTO.java
└── config/               # Configuration beans
    ├── PersistenceConfig.java / ClinicTimeZone.java
    ├── WeekConfig.java / HolidayConfig.java
    ├── SecurityConfig.java (Phase 2 stub)
    ├── WebConfig.java / GlobalModelAttributes.java
    └── PatientCategoryStore.java / PatientSeedDataFactory.java

src/main/resources/
├── templates/            # Thymeleaf HTML templates
│   ├── index.html / error.html
│   ├── auth/login.html
│   ├── booking/ (dashboard, lookup, select-slot, confirmation, cancelled)
│   ├── patients/ (register, list, detail, edit, assign-category)
│   ├── staff/ (dashboard, occupancy, audit-log, reports, slot-grid,
│   │           weekly-selection-result, system-health)
│   └── fragments/ (layout, header, footer, slot-grid, occupancy-panel)
├── static/css/ (agn-theme.css, design-tokens.css)
└── [application.properties, application-test.properties]
```

## Java Conventions

| Convention | Standard |
|-----------|----------|
| **Naming** | `PatientService` (classes), `getPatientById()` (methods), `SLOT_DURATION_MINUTES` (constants) |
| **Annotations** | `@Entity`, `@Service`, `@Repository`, `@Controller`, `@Transactional` |
| **Method visibility** | Public for services; private for helpers; protected only when extending |
| **Exception handling** | Throw checked/unchecked explicitly; no silent catches; log before re-throw |
| **Logging** | SLF4J (`private static final Logger log = LoggerFactory.getLogger(Class.class);`) |
| **Null safety** | Use Optional<T>; avoid null returns from public methods |
| **Testing** | JUnit 5; class name = `{Class}Test.java`; one logical test per method (@Test) |

## Repository Pattern

**All database access via repositories:**

```java
public interface PatientRepository extends JpaRepository<Patient, Long> {
    List<Patient> findByCategory(PatientCategory category);
    Optional<Patient> findByIdAndStatus(Long id, PatientStatus status);
    @Query("SELECT p FROM Patient p WHERE p.lastConsultationDate < :cutoff")
    List<Patient> findOverduePatients(LocalDateTime cutoff);
}
```

**No direct SQL in services** — only repository queries  
**Pessimistic locks only in repositories** — never in service layer

## Transaction Boundaries

- **Service methods**: `@Transactional` (read-only when applicable)
- **Lock scope**: Single transaction, lock released on commit
- **Rollback**: Automatic on exception; use `@Transactional(rollbackFor = Exception.class)` for checked exceptions

## Testing Strategy

| Type | Framework | Location | Example |
|------|-----------|----------|---------|
| **Unit** | JUnit 5 + Mockito | `src/test/java/.../service/` | `ScoringServiceTest` — mock repositories |
| **Integration** | Spring Test + H2 | `src/test/java/.../repository/` | `PatientRepositoryTest` — real DB queries |
| **Algorithm** | Parameterized JUnit | `src/test/java/.../service/` | `ScoringAlgorithmTest` — edge cases (ties, overdue, priority order) |
| **Controller** | MockMvc | `src/test/java/.../controller/` | `BookingControllerTest` — HTTP 200, 409 (conflict) |
| **Concurrency** | TestNG (optional) + Thread pool | `src/test/java/.../concurrency/` | Concurrent slot booking under contention |

## Profile Management

**Development** (`application.properties`):
```
spring.jpa.show-sql=true
spring.jpa.hibernate.ddl-auto=update
logging.level.com.PRIORIZASUS=DEBUG
```

**Test** (`application-test.properties`):
```
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop
logging.level.com.PRIORIZASUS=DEBUG
```

**Production** (environment variables):
```
DATABASE_URL=jdbc:postgresql://host:5432/PRIORIZASUS
JPA_SHOW_SQL=false
JPA_DDL_AUTO=validate
LOGGING_LEVEL=INFO
```

## Error Handling

**Standard Exception Hierarchy**:

```
RuntimeException
├── SlotLockException (extends RuntimeException)
├── InvalidPatientStateException (extends RuntimeException)
├── IneligibleForBookingException (extends RuntimeException)
└── DuplicateAppointmentException (extends RuntimeException)
```

**Controller advice** (global error handler):
- Returns JSON with error code + message
- Logs exception details
- Returns appropriate HTTP status (400, 409, 500)

## Deployment & Build

**Maven**:
```
mvn clean package          # Build JAR
mvn spring-boot:run        # Run locally
mvn test                   # Run all tests
```

**Java options**:
```
-Xmx512m (heap for Spring Boot + DB)
-Dspring.profiles.active=production (select profile)
```

---

**Last Updated**: May 23, 2026  
**Maintainer**: PRIORIZASUS Team
