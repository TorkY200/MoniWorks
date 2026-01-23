package com.example.application.domain;

import java.time.Instant;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents an imported bank statement file. Used to track imported statements and prevent
 * duplicate imports.
 */
@Entity
@Table(
    name = "bank_statement_import",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"company_id", "account_id", "file_hash"})
    })
public class BankStatementImport {

  public enum SourceType {
    QIF,
    OFX,
    QFX,
    QBO,
    CSV
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @Column(name = "imported_at", nullable = false)
  private Instant importedAt;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 20)
  private SourceType sourceType;

  @NotBlank
  @Size(max = 255)
  @Column(name = "source_name", nullable = false, length = 255)
  private String sourceName;

  @NotBlank
  @Size(max = 64)
  @Column(name = "file_hash", nullable = false, length = 64)
  private String fileHash;

  @Column(name = "total_items", nullable = false)
  private int totalItems = 0;

  @PrePersist
  protected void onCreate() {
    importedAt = Instant.now();
  }

  // Constructors
  public BankStatementImport() {}

  public BankStatementImport(
      Company company, Account account, SourceType sourceType, String sourceName, String fileHash) {
    this.company = company;
    this.account = account;
    this.sourceType = sourceType;
    this.sourceName = sourceName;
    this.fileHash = fileHash;
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

  public Account getAccount() {
    return account;
  }

  public void setAccount(Account account) {
    this.account = account;
  }

  public Instant getImportedAt() {
    return importedAt;
  }

  public SourceType getSourceType() {
    return sourceType;
  }

  public void setSourceType(SourceType sourceType) {
    this.sourceType = sourceType;
  }

  public String getSourceName() {
    return sourceName;
  }

  public void setSourceName(String sourceName) {
    this.sourceName = sourceName;
  }

  public String getFileHash() {
    return fileHash;
  }

  public void setFileHash(String fileHash) {
    this.fileHash = fileHash;
  }

  public int getTotalItems() {
    return totalItems;
  }

  public void setTotalItems(int totalItems) {
    this.totalItems = totalItems;
  }
}
