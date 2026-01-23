package com.example.application.domain;

import java.time.Instant;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents an audit trail entry for tracking all significant changes. Audit events are immutable
 * - they can only be created, never modified or deleted.
 */
@Entity
@Table(
    name = "audit_event",
    indexes = {
      @Index(name = "idx_audit_company_time", columnList = "company_id, created_at"),
      @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id")
    })
public class AuditEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id")
  private Company company;

  @NotNull
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_id")
  private User actor;

  @NotBlank
  @Size(max = 50)
  @Column(name = "event_type", nullable = false, length = 50)
  private String eventType;

  @NotBlank
  @Size(max = 50)
  @Column(name = "entity_type", nullable = false, length = 50)
  private String entityType;

  @Column(name = "entity_id")
  private Long entityId;

  @Size(max = 255)
  @Column(length = 255)
  private String summary;

  @Column(name = "details_json", columnDefinition = "TEXT")
  private String detailsJson;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  // Constructors
  public AuditEvent() {}

  public AuditEvent(
      Company company,
      User actor,
      String eventType,
      String entityType,
      Long entityId,
      String summary) {
    this.company = company;
    this.actor = actor;
    this.eventType = eventType;
    this.entityType = entityType;
    this.entityId = entityId;
    this.summary = summary;
  }

  // Getters only - audit events are immutable
  public Long getId() {
    return id;
  }

  public Company getCompany() {
    return company;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public User getActor() {
    return actor;
  }

  public String getEventType() {
    return eventType;
  }

  public String getEntityType() {
    return entityType;
  }

  public Long getEntityId() {
    return entityId;
  }

  public String getSummary() {
    return summary;
  }

  public String getDetailsJson() {
    return detailsJson;
  }

  // Only setter for detailsJson since it may be set after construction
  public void setDetailsJson(String detailsJson) {
    this.detailsJson = detailsJson;
  }
}
