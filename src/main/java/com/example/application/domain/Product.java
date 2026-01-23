package com.example.application.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents a product or service that can be sold or purchased. Products store pricing, tax
 * defaults, and optional inventory tracking. Used for invoice and bill line auto-fill.
 */
@Entity
@Table(
    name = "product",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"company_id", "code"})})
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @NotBlank
  @Size(max = 31)
  @Column(nullable = false, length = 31)
  private String code;

  @NotBlank
  @Size(max = 100)
  @Column(nullable = false, length = 100)
  private String name;

  @Size(max = 500)
  @Column(length = 500)
  private String description;

  @Size(max = 50)
  @Column(length = 50)
  private String category;

  // Purchase price (cost)
  @Column(name = "buy_price", precision = 19, scale = 2)
  private BigDecimal buyPrice;

  // Sales price
  @Column(name = "sell_price", precision = 19, scale = 2)
  private BigDecimal sellPrice;

  // Default tax code for this product
  @Size(max = 10)
  @Column(name = "tax_code", length = 10)
  private String taxCode;

  // Whether this product tracks inventory (Phase 2 feature)
  @Column(name = "is_inventoried", nullable = false)
  private boolean inventoried = false;

  // Optional barcode
  @Size(max = 50)
  @Column(length = 50)
  private String barcode;

  // Optional image attachment reference
  @Column(name = "image_attachment_id")
  private Long imageAttachmentId;

  // Sticky note that appears when product is selected
  @Size(max = 500)
  @Column(name = "sticky_note", length = 500)
  private String stickyNote;

  // Default income account for sales
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sales_account_id")
  private Account salesAccount;

  // Default expense account for purchases
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "purchase_account_id")
  private Account purchaseAccount;

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
  public Product() {}

  public Product(Company company, String code, String name) {
    this.company = company;
    this.code = code;
    this.name = name;
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

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public BigDecimal getBuyPrice() {
    return buyPrice;
  }

  public void setBuyPrice(BigDecimal buyPrice) {
    this.buyPrice = buyPrice;
  }

  public BigDecimal getSellPrice() {
    return sellPrice;
  }

  public void setSellPrice(BigDecimal sellPrice) {
    this.sellPrice = sellPrice;
  }

  public String getTaxCode() {
    return taxCode;
  }

  public void setTaxCode(String taxCode) {
    this.taxCode = taxCode;
  }

  public boolean isInventoried() {
    return inventoried;
  }

  public void setInventoried(boolean inventoried) {
    this.inventoried = inventoried;
  }

  public String getBarcode() {
    return barcode;
  }

  public void setBarcode(String barcode) {
    this.barcode = barcode;
  }

  public Long getImageAttachmentId() {
    return imageAttachmentId;
  }

  public void setImageAttachmentId(Long imageAttachmentId) {
    this.imageAttachmentId = imageAttachmentId;
  }

  public String getStickyNote() {
    return stickyNote;
  }

  public void setStickyNote(String stickyNote) {
    this.stickyNote = stickyNote;
  }

  public Account getSalesAccount() {
    return salesAccount;
  }

  public void setSalesAccount(Account salesAccount) {
    this.salesAccount = salesAccount;
  }

  public Account getPurchaseAccount() {
    return purchaseAccount;
  }

  public void setPurchaseAccount(Account purchaseAccount) {
    this.purchaseAccount = purchaseAccount;
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
