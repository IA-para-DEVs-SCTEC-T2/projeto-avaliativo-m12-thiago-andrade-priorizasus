package com.priorizasus.priorizasus.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.priorizasus.priorizasus.entity.AuditActionType;
import com.priorizasus.priorizasus.entity.AuditLog;
import com.priorizasus.priorizasus.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

  @Mock private AuditLogRepository auditLogRepository;
  private AuditLogService auditLogService;

  @BeforeEach
  void setUp() {
    auditLogService = new AuditLogService(auditLogRepository);
  }

  @Test
  @DisplayName("findAll returns all logs sorted by timestamp desc")
  void findAllReturnsSorted() {
    when(auditLogRepository.findAll(any(Sort.class))).thenReturn(List.of());

    var result = auditLogService.findAll();

    assertNotNull(result);
    verify(auditLogRepository).findAll(any(Sort.class));
  }

  @Test
  @DisplayName("findByActionType filters by type")
  void findByActionTypeDelegates() {
    AuditLog log = new AuditLog(AuditActionType.BOOKING, "system", "test");
    when(auditLogRepository.findByActionType(AuditActionType.BOOKING)).thenReturn(List.of(log));

    var result = auditLogService.findByActionType(AuditActionType.BOOKING);

    assertEquals(1, result.size());
  }

  @Test
  @DisplayName("findByTimestampBetween filters by range")
  void findByTimestampBetweenDelegates() {
    Instant from = Instant.parse("2026-05-01T00:00:00Z");
    Instant to = Instant.parse("2026-05-31T23:59:59Z");
    when(auditLogRepository.findByTimestampBetween(from, to)).thenReturn(List.of());

    var result = auditLogService.findByTimestampBetween(from, to);

    assertNotNull(result);
  }

  @Test
  @DisplayName("findByActionTypeAndTimestampBetween filters by both")
  void findByActionTypeAndTimestampBetweenDelegates() {
    Instant from = Instant.parse("2026-05-01T00:00:00Z");
    Instant to = Instant.parse("2026-05-31T23:59:59Z");
    when(auditLogRepository.findByActionTypeAndTimestampBetween(AuditActionType.BOOKING, from, to))
        .thenReturn(List.of());

    var result =
        auditLogService.findByActionTypeAndTimestampBetween(AuditActionType.BOOKING, from, to);

    assertNotNull(result);
  }
}
