package com.priorizasus.priorizasus.service;

import com.priorizasus.priorizasus.annotation.ReqId;
import com.priorizasus.priorizasus.entity.AuditActionType;
import com.priorizasus.priorizasus.entity.AuditLog;
import com.priorizasus.priorizasus.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for AuditLog queries — keeps repository access out of controllers per ArchUnit rules. */
@Service
public class AuditLogService {

  private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

  private final AuditLogRepository auditLogRepository;

  public AuditLogService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  @ReqId("SD-005")
  @Transactional(readOnly = true)
  public List<AuditLog> findAll() {
    return auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"));
  }

  @ReqId("SD-005")
  @Transactional(readOnly = true)
  public List<AuditLog> findByActionType(AuditActionType actionType) {
    return auditLogRepository.findByActionType(actionType);
  }

  @ReqId("SD-005")
  @Transactional(readOnly = true)
  public List<AuditLog> findByTimestampBetween(Instant from, Instant to) {
    return auditLogRepository.findByTimestampBetween(from, to);
  }

  @ReqId("SD-005")
  @Transactional(readOnly = true)
  public List<AuditLog> findByActionTypeAndTimestampBetween(
      AuditActionType actionType, Instant from, Instant to) {
    return auditLogRepository.findByActionTypeAndTimestampBetween(actionType, from, to);
  }
}
