package com.example.application.domain;

import java.time.Instant;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents an allocation rule for auto-suggesting coding during bank reconciliation. Rules match
 * bank feed item descriptions to suggest accounts and tax codes.
 */
@Entity
@Table(
    name = "allocation_rule",
    indexes = {
      @Index(name = "idx_allocation_rule_lookup", columnList = "company_id, enabled, priority")
    })
public class AllocationRule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @Column(nullable = false)
  private int priority = 0;

  @NotBlank
  @Size(max = 100)
  @Column(name = "rule_name", nullable = false, length = 100)
  private String ruleName;

  @NotBlank
  @Size(max = 500)
  @Column(name = "match_expression", nullable = false, length = 500)
  private String matchExpression;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "target_account_id", nullable = false)
  private Account targetAccount;

  @Size(max = 10)
  @Column(name = "target_tax_code", length = 10)
  private String targetTaxCode;

  @Size(max = 255)
  @Column(name = "memo_template", length = 255)
  private String memoTemplate;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  // Constructors
  public AllocationRule() {}

  public AllocationRule(
      Company company, String ruleName, String matchExpression, Account targetAccount) {
    this.company = company;
    this.ruleName = ruleName;
    this.matchExpression = matchExpression;
    this.targetAccount = targetAccount;
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Company getCompany() {
    return company;
  }

  public void setCompany(Company company) {
    this.company = company;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  public String getRuleName() {
    return ruleName;
  }

  public void setRuleName(String ruleName) {
    this.ruleName = ruleName;
  }

  public String getMatchExpression() {
    return matchExpression;
  }

  public void setMatchExpression(String matchExpression) {
    this.matchExpression = matchExpression;
  }

  public Account getTargetAccount() {
    return targetAccount;
  }

  public void setTargetAccount(Account targetAccount) {
    this.targetAccount = targetAccount;
  }

  public String getTargetTaxCode() {
    return targetTaxCode;
  }

  public void setTargetTaxCode(String targetTaxCode) {
    this.targetTaxCode = targetTaxCode;
  }

  public String getMemoTemplate() {
    return memoTemplate;
  }

  public void setMemoTemplate(String memoTemplate) {
    this.memoTemplate = memoTemplate;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  /**
   * Tests if the given description matches this rule's expression. Currently supports simple
   * CONTAINS matching.
   */
  public boolean matches(String description) {
    if (description == null || matchExpression == null) {
      return false;
    }

    // Simple CONTAINS matching (case-insensitive)
    // Format: "CONTAINS 'text'" or just "text" for simple contains
    String expr = matchExpression.trim();

    if (expr.toUpperCase().startsWith("CONTAINS ")) {
      String pattern = expr.substring(9).trim();
      // Remove quotes if present
      if (pattern.startsWith("'") && pattern.endsWith("'")) {
        pattern = pattern.substring(1, pattern.length() - 1);
      }
      return description.toLowerCase().contains(pattern.toLowerCase());
    }

    // Default: simple contains
    return description.toLowerCase().contains(expr.toLowerCase());
  }
}
