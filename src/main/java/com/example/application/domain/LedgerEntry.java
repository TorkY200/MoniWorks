package com.example.application.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents an immutable ledger entry created when a transaction is posted. These entries form the
 * official record of all accounting activity. Ledger entries cannot be modified - corrections
 * require reversals.
 */
@Entity
@Table(
    name = "ledger_entry",
    indexes = {
      @Index(name = "idx_ledger_company_date", columnList = "company_id, entry_date"),
      @Index(name = "idx_ledger_account", columnList = "account_id")
    })
public class LedgerEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transaction_id", nullable = false)
  private Transaction transaction;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transaction_line_id", nullable = false)
  private TransactionLine transactionLine;

  @NotNull
  @Column(name = "entry_date", nullable = false)
  private LocalDate entryDate;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @NotNull
  @Column(name = "amount_dr", nullable = false, precision = 19, scale = 2)
  private BigDecimal amountDr = BigDecimal.ZERO;

  @NotNull
  @Column(name = "amount_cr", nullable = false, precision = 19, scale = 2)
  private BigDecimal amountCr = BigDecimal.ZERO;

  @Size(max = 10)
  @Column(name = "tax_code", length = 10)
  private String taxCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id")
  private Department department;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  // Constructors
  public LedgerEntry() {}

  public LedgerEntry(Company company, Transaction transaction, TransactionLine line) {
    this.company = company;
    this.transaction = transaction;
    this.transactionLine = line;
    this.entryDate = transaction.getTransactionDate();
    this.account = line.getAccount();
    this.taxCode = line.getTaxCode();
    this.department = line.getDepartment();

    if (line.isDebit()) {
      this.amountDr = line.getAmount();
      this.amountCr = BigDecimal.ZERO;
    } else {
      this.amountDr = BigDecimal.ZERO;
      this.amountCr = line.getAmount();
    }
  }

  // Getters only - ledger entries are immutable after creation
  public Long getId() {
    return id;
  }

  public Company getCompany() {
    return company;
  }

  public Transaction getTransaction() {
    return transaction;
  }

  public TransactionLine getTransactionLine() {
    return transactionLine;
  }

  public LocalDate getEntryDate() {
    return entryDate;
  }

  public Account getAccount() {
    return account;
  }

  public BigDecimal getAmountDr() {
    return amountDr;
  }

  public BigDecimal getAmountCr() {
    return amountCr;
  }

  public String getTaxCode() {
    return taxCode;
  }

  public Department getDepartment() {
    return department;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  // Helper method to get net amount (debit positive, credit negative)
  public BigDecimal getNetAmount() {
    return amountDr.subtract(amountCr);
  }
}
