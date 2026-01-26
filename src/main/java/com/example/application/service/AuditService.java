package com.example.application.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.AuditEvent;
import com.example.application.domain.AuditRetentionSettings;
import com.example.application.domain.Company;
import com.example.application.domain.CompanySettings;
import com.example.application.domain.User;
import com.example.application.repository.AuditEventRepository;
import com.example.application.repository.CompanyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for recording and querying audit events. All significant actions in the system are logged
 * for accountability. Also handles audit retention policy enforcement.
 */
@Service
@Transactional
public class AuditService {

  private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

  private final AuditEventRepository auditEventRepository;
  private final CompanyRepository companyRepository;
  private final ObjectMapper objectMapper;

  public AuditService(
      AuditEventRepository auditEventRepository, CompanyRepository companyRepository) {
    this.auditEventRepository = auditEventRepository;
    this.companyRepository = companyRepository;
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

  // ============================================================
  // Retention Policy Methods
  // ============================================================

  /**
   * Scheduled task to enforce audit retention policies for all companies. Runs daily at 4:00 AM.
   * Only processes companies with enabled retention policies.
   */
  @Scheduled(cron = "0 0 4 * * *")
  public void enforceAllRetentionPolicies() {
    logger.info("Starting scheduled audit retention policy enforcement");
    List<Company> companies = companyRepository.findAll();
    int totalDeleted = 0;

    for (Company company : companies) {
      try {
        int deleted = enforceRetentionPolicy(company);
        if (deleted > 0) {
          totalDeleted += deleted;
          logger.info(
              "Enforced retention policy for company {}: deleted {} events",
              company.getName(),
              deleted);
        }
      } catch (Exception e) {
        logger.error(
            "Failed to enforce retention policy for company {}: {}",
            company.getName(),
            e.getMessage());
      }
    }

    logger.info("Completed audit retention policy enforcement. Total deleted: {}", totalDeleted);
  }

  /**
   * Enforces the retention policy for a specific company. Returns the number of events deleted.
   *
   * @param company the company to enforce the policy for
   * @return number of audit events deleted
   */
  public int enforceRetentionPolicy(Company company) {
    AuditRetentionSettings settings = getRetentionSettings(company);

    if (!settings.isEnabled()) {
      return 0; // Policy not enabled, keep all events
    }

    Instant cutoff = Instant.now().minus(settings.getEffectiveRetentionDays(), ChronoUnit.DAYS);

    int deleted;
    if (settings.shouldKeepLoginEvents()) {
      deleted =
          auditEventRepository.deleteByCompanyAndCreatedAtBeforeExcludingLoginEvents(
              company, cutoff);
    } else {
      deleted = auditEventRepository.deleteByCompanyAndCreatedAtBefore(company, cutoff);
    }

    if (deleted > 0) {
      // Log the retention enforcement (this event is exempt from future deletion)
      logEvent(
          company,
          null,
          "RETENTION_POLICY_ENFORCED",
          "AuditEvent",
          null,
          String.format(
              "Retention policy enforced: deleted %d audit events older than %d days",
              deleted, settings.getEffectiveRetentionDays()));
    }

    return deleted;
  }

  /**
   * Gets the retention settings for a company.
   *
   * @param company the company
   * @return retention settings (never null)
   */
  @Transactional(readOnly = true)
  public AuditRetentionSettings getRetentionSettings(Company company) {
    if (company == null
        || company.getSettingsJson() == null
        || company.getSettingsJson().isBlank()) {
      return new AuditRetentionSettings();
    }

    try {
      CompanySettings settings =
          objectMapper.readValue(company.getSettingsJson(), CompanySettings.class);
      return settings.getOrCreateAuditRetention();
    } catch (JsonProcessingException e) {
      logger.warn("Failed to parse company settings for {}: {}", company.getName(), e.getMessage());
      return new AuditRetentionSettings();
    }
  }

  /**
   * Previews how many audit events would be deleted by the retention policy. Does not actually
   * delete anything.
   *
   * @param company the company
   * @param retentionDays number of days to retain
   * @param keepLoginEvents whether to keep login events
   * @return number of events that would be deleted
   */
  @Transactional(readOnly = true)
  public long previewRetentionPolicy(Company company, int retentionDays, boolean keepLoginEvents) {
    Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

    if (keepLoginEvents) {
      return auditEventRepository.countByCompanyAndCreatedAtBeforeExcludingLoginEvents(
          company, cutoff);
    } else {
      return auditEventRepository.countByCompanyAndCreatedAtBefore(company, cutoff);
    }
  }

  /**
   * Gets the count of total audit events for a company.
   *
   * @param company the company
   * @return total event count
   */
  @Transactional(readOnly = true)
  public long countByCompany(Company company) {
    return auditEventRepository
        .findByCompanyOrderByCreatedAtDesc(
            company, org.springframework.data.domain.Pageable.unpaged())
        .getTotalElements();
  }

  // ============================================================
  // CSV Export Methods
  // ============================================================

  /**
   * Exports audit events to CSV format for compliance and reporting.
   *
   * @param events the list of audit events to export
   * @param company the company (for header information)
   * @return CSV content as byte array with UTF-8 BOM for Excel compatibility
   */
  public byte[] exportToCsv(List<AuditEvent> events, Company company) {
    StringBuilder csv = new StringBuilder();

    // UTF-8 BOM for Excel compatibility
    csv.append("\uFEFF");

    // Header
    csv.append(escapeCsvField(company != null ? company.getName() : "All Companies"))
        .append(" - Audit Trail Export\n");
    csv.append("Generated: ")
        .append(
            java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
        .append("\n");
    csv.append("Total Events: ").append(events.size()).append("\n\n");

    // Column headers
    csv.append("Timestamp,Event Type,Actor,Entity Type,Entity ID,Summary,Details\n");

    // Data rows
    java.time.format.DateTimeFormatter formatter =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault());

    for (AuditEvent event : events) {
      // Timestamp
      csv.append(event.getCreatedAt() != null ? formatter.format(event.getCreatedAt()) : "")
          .append(",");

      // Event Type
      csv.append(escapeCsvField(event.getEventType())).append(",");

      // Actor
      String actor = event.getActor() != null ? event.getActor().getDisplayName() : "System";
      csv.append(escapeCsvField(actor)).append(",");

      // Entity Type
      csv.append(escapeCsvField(event.getEntityType())).append(",");

      // Entity ID
      csv.append(event.getEntityId() != null ? event.getEntityId().toString() : "").append(",");

      // Summary
      csv.append(escapeCsvField(event.getSummary())).append(",");

      // Details JSON
      csv.append(escapeCsvField(event.getDetailsJson())).append("\n");
    }

    return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * Escapes a field value for CSV format. If the field contains comma, quote, or newline, it is
   * wrapped in quotes and internal quotes are doubled.
   */
  private String escapeCsvField(String value) {
    if (value == null) return "";
    // If field contains comma, quote, or newline, wrap in quotes and escape quotes
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }
}
