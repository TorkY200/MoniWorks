package com.example.application.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Audit event retention policy settings stored in Company.settingsJson. Configures how long audit
 * events are retained and whether automatic cleanup is enabled.
 *
 * <p>Per spec 14: "Users cannot delete audit events; retention policy is configurable but defaults
 * to 'keep'."
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditRetentionSettings {

  // Whether retention policy is enabled (default: false = keep forever)
  private Boolean enabled;

  // Number of days to retain audit events (only used if enabled = true)
  // Minimum 90 days for compliance, typical values: 365, 730 (2 years), 2555 (7 years)
  private Integer retentionDays;

  // Whether to exclude certain event types from deletion (login/logout events are often kept
  // longer)
  private Boolean keepLoginEvents;

  // Default values
  public static final boolean DEFAULT_ENABLED = false; // Keep forever by default
  public static final int DEFAULT_RETENTION_DAYS = 2555; // 7 years (accounting records)
  public static final int MINIMUM_RETENTION_DAYS = 90; // Minimum for compliance
  public static final boolean DEFAULT_KEEP_LOGIN_EVENTS = true;

  public AuditRetentionSettings() {
    this.enabled = DEFAULT_ENABLED;
    this.retentionDays = DEFAULT_RETENTION_DAYS;
    this.keepLoginEvents = DEFAULT_KEEP_LOGIN_EVENTS;
  }

  // Getters and Setters

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Integer getRetentionDays() {
    return retentionDays;
  }

  public void setRetentionDays(Integer retentionDays) {
    // Enforce minimum retention period
    if (retentionDays != null && retentionDays < MINIMUM_RETENTION_DAYS) {
      this.retentionDays = MINIMUM_RETENTION_DAYS;
    } else {
      this.retentionDays = retentionDays;
    }
  }

  public Boolean getKeepLoginEvents() {
    return keepLoginEvents;
  }

  public void setKeepLoginEvents(Boolean keepLoginEvents) {
    this.keepLoginEvents = keepLoginEvents;
  }

  // Helper methods

  /** Returns whether retention policy is enabled. Defaults to false (keep forever). */
  public boolean isEnabled() {
    return enabled != null && enabled;
  }

  /** Returns effective retention days with fallback to default. */
  public int getEffectiveRetentionDays() {
    return retentionDays != null
        ? Math.max(retentionDays, MINIMUM_RETENTION_DAYS)
        : DEFAULT_RETENTION_DAYS;
  }

  /** Returns whether login events should be excluded from cleanup. Defaults to true. */
  public boolean shouldKeepLoginEvents() {
    return keepLoginEvents == null || keepLoginEvents;
  }

  /**
   * Validates the settings.
   *
   * @return true if settings are valid
   */
  public boolean isValid() {
    if (isEnabled()) {
      return retentionDays != null && retentionDays >= MINIMUM_RETENTION_DAYS;
    }
    return true;
  }

  /**
   * Returns a human-readable description of the retention policy.
   *
   * @return description string
   */
  public String getDescription() {
    if (!isEnabled()) {
      return "Keep all audit events (no automatic deletion)";
    }
    int days = getEffectiveRetentionDays();
    String period;
    if (days >= 365) {
      int years = days / 365;
      period = years == 1 ? "1 year" : years + " years";
    } else {
      period = days + " days";
    }
    String loginNote = shouldKeepLoginEvents() ? " (login events excluded)" : "";
    return "Delete audit events older than " + period + loginNote;
  }
}
