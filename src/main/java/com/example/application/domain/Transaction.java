package com.example.application.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents a financial transaction (payment, receipt, journal, transfer). Transactions contain
 * lines that debit/credit accounts. Posted transactions are immutable - corrections require
 * reversals.
 */
@Entity
@Table(name = "transaction")
public class Transaction {

  public enum TransactionType {
    PAYMENT,
    RECEIPT,
    JOURNAL,
    TRANSFER
  }

  public enum Status {
    DRAFT,
    POSTED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TransactionType type;

  @NotNull
  @Column(name = "transaction_date", nullable = false)
  private LocalDate transactionDate;

  @Size(max = 255)
  @Column(length = 255)
  private String description;

  @Size(max = 50)
  @Column(length = 50)
  private String reference;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Status status = Status.DRAFT;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by")
  private User createdBy;

  @Column(name = "posted_at")
  private Instant postedAt;

  @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("lineIndex ASC")
  private List<TransactionLine> lines = new ArrayList<>();

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
  public Transaction() {}

  public Transaction(Company company, TransactionType type, LocalDate transactionDate) {
    this.company = company;
    this.type = type;
    this.transactionDate = transactionDate;
  }

  // Helper methods
  public void addLine(TransactionLine line) {
    lines.add(line);
    line.setTransaction(this);
    line.setLineIndex(lines.size());
  }

  public void removeLine(TransactionLine line) {
    lines.remove(line);
    line.setTransaction(null);
  }

  public boolean isPosted() {
    return status == Status.POSTED;
  }

  public boolean isDraft() {
    return status == Status.DRAFT;
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

  public TransactionType getType() {
    return type;
  }

  public void setType(TransactionType type) {
    this.type = type;
  }

  public LocalDate getTransactionDate() {
    return transactionDate;
  }

  public void setTransactionDate(LocalDate transactionDate) {
    this.transactionDate = transactionDate;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(User createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getPostedAt() {
    return postedAt;
  }

  public void setPostedAt(Instant postedAt) {
    this.postedAt = postedAt;
  }

  public List<TransactionLine> getLines() {
    return lines;
  }

  public void setLines(List<TransactionLine> lines) {
    this.lines = lines;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
