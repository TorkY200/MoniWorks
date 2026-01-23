package com.example.application.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents a tax code (e.g., GST 15%, Zero-rated, Exempt). Tax codes define the rate and type for
 * tax calculations.
 */
@Entity
@Table(
    name = "tax_code",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"company_id", "code"})})
public class TaxCode {

  public enum TaxType {
    STANDARD,
    ZERO_RATED,
    EXEMPT,
    OUT_OF_SCOPE
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @NotBlank
  @Size(max = 10)
  @Column(nullable = false, length = 10)
  private String code;

  @NotBlank
  @Size(max = 50)
  @Column(nullable = false, length = 50)
  private String name;

  @NotNull
  @Column(nullable = false, precision = 5, scale = 4)
  private BigDecimal rate;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TaxType type;

  @Size(max = 20)
  @Column(name = "report_box", length = 20)
  private String reportBox;

  @Column(nullable = false)
  private boolean active = true;

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
  public TaxCode() {}

  public TaxCode(Company company, String code, String name, BigDecimal rate, TaxType type) {
    this.company = company;
    this.code = code;
    this.name = name;
    this.rate = rate;
    this.type = type;
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

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public BigDecimal getRate() {
    return rate;
  }

  public void setRate(BigDecimal rate) {
    this.rate = rate;
  }

  public TaxType getType() {
    return type;
  }

  public void setType(TaxType type) {
    this.type = type;
  }

  public String getReportBox() {
    return reportBox;
  }

  public void setReportBox(String reportBox) {
    this.reportBox = reportBox;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
