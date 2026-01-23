package com.example.application.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents a company/tenant in the multi-tenant accounting system. All accounting data is scoped
 * to a company.
 */
@Entity
@Table(name = "company")
public class Company {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Size(max = 100)
  @Column(nullable = false, length = 100)
  private String name;

  @NotBlank
  @Size(min = 2, max = 2)
  @Column(nullable = false, length = 2)
  private String country;

  @NotBlank
  @Size(min = 3, max = 3)
  @Column(name = "base_currency", nullable = false, length = 3)
  private String baseCurrency;

  @NotNull
  @Column(name = "fiscal_year_start", nullable = false)
  private LocalDate fiscalYearStart;

  @Column(name = "settings_json", columnDefinition = "TEXT")
  private String settingsJson;

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
  public Company() {}

  public Company(String name, String country, String baseCurrency, LocalDate fiscalYearStart) {
    this.name = name;
    this.country = country;
    this.baseCurrency = baseCurrency;
    this.fiscalYearStart = fiscalYearStart;
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getBaseCurrency() {
    return baseCurrency;
  }

  public void setBaseCurrency(String baseCurrency) {
    this.baseCurrency = baseCurrency;
  }

  public LocalDate getFiscalYearStart() {
    return fiscalYearStart;
  }

  public void setFiscalYearStart(LocalDate fiscalYearStart) {
    this.fiscalYearStart = fiscalYearStart;
  }

  public String getSettingsJson() {
    return settingsJson;
  }

  public void setSettingsJson(String settingsJson) {
    this.settingsJson = settingsJson;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
