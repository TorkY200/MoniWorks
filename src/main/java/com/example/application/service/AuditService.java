package com.example.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.AuditEvent;
import com.example.application.domain.Company;
import com.example.application.domain.User;
import com.example.application.repository.AuditEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for recording and querying audit events. All significant actions in the system are logged
 * for accountability.
 */
@Service
@Transactional
public class AuditService {

  private final AuditEventRepository auditEventRepository;
  private final ObjectMapper objectMapper;

  public AuditService(AuditEventRepository auditEventRepository) {
    this.auditEventRepository = auditEventRepository;
    this.objectMapper = new ObjectMapper();
  }

  /** Logs an audit event with basic information. */
  public AuditEvent logEvent(
      Company company,
      User actor,
      String eventType,
      String entityType,
      Long entityId,
      String summary) {
    AuditEvent event = new AuditEvent(company, actor, eventType, entityType, entityId, summary);
    return auditEventRepository.save(event);
  }

  /** Logs an audit event with additional details. */
  public AuditEvent logEvent(
      Company company,
      User actor,
      String eventType,
      String entityType,
      Long entityId,
      String summary,
      Map<String, Object> details) {
    AuditEvent event = new AuditEvent(company, actor, eventType, entityType, entityId, summary);

    if (details != null && !details.isEmpty()) {
      try {
        event.setDetailsJson(objectMapper.writeValueAsString(details));
      } catch (JsonProcessingException e) {
        // Log warning but don't fail the audit
        event.setDetailsJson("{\"error\":\"Failed to serialize details\"}");
      }
    }

    return auditEventRepository.save(event);
  }

  /** Logs a login event. */
  public AuditEvent logLogin(User user) {
    return logEvent(
        null, user, "USER_LOGIN", "User", user.getId(), "User logged in: " + user.getEmail());
  }

  /** Logs a logout event. */
  public AuditEvent logLogout(User user) {
    return logEvent(
        null, user, "USER_LOGOUT", "User", user.getId(), "User logged out: " + user.getEmail());
  }

  @Transactional(readOnly = true)
  public Page<AuditEvent> findByCompany(Company company, Pageable pageable) {
    return auditEventRepository.findByCompanyOrderByCreatedAtDesc(company, pageable);
  }

  @Transactional(readOnly = true)
  public List<AuditEvent> findByCompanyAndDateRange(Company company, Instant start, Instant end) {
    return auditEventRepository.findByCompanyAndDateRange(company, start, end);
  }

  @Transactional(readOnly = true)
  public List<AuditEvent> findByEntity(String entityType, Long entityId) {
    return auditEventRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
        entityType, entityId);
  }

  @Transactional(readOnly = true)
  public List<AuditEvent> findByCompanyAndEventType(Company company, String eventType) {
    return auditEventRepository.findByCompanyAndEventType(company, eventType);
  }
}
