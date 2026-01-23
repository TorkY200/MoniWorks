package com.example.application.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents a customer or supplier contact in the system. Contacts are company-scoped and can be
 * customers, suppliers, or both. Used for invoicing, bill payments, and tracking business
 * relationships.
 */
@Entity
@Table(
    name = "contact",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"company_id", "code"})})
public class Contact {

  public enum ContactType {
    CUSTOMER,
    SUPPLIER,
    BOTH
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @NotBlank
  @Size(max = 11)
  @Column(nullable = false, length = 11)
  private String code;

  @NotBlank
  @Size(max = 100)
  @Column(nullable = false, length = 100)
  private String name;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ContactType type = ContactType.CUSTOMER;

  @Size(max = 50)
  @Column(length = 50)
  private String category;

  @Size(max = 20)
  @Column(name = "color_tag", length = 20)
  private String colorTag;

  // Primary address fields
  @Size(max = 255)
  @Column(name = "address_line1", length = 255)
  private String addressLine1;

  @Size(max = 255)
  @Column(name = "address_line2", length = 255)
  private String addressLine2;

  @Size(max = 100)
  @Column(length = 100)
  private String city;

  @Size(max = 100)
  @Column(length = 100)
  private String region;

  @Size(max = 20)
  @Column(name = "postal_code", length = 20)
  private String postalCode;

  @Size(max = 2)
  @Column(length = 2)
  private String country;

  // Contact details
  @Size(max = 50)
  @Column(length = 50)
  private String phone;

  @Size(max = 50)
  @Column(length = 50)
  private String mobile;

  @Size(max = 100)
  @Column(length = 100)
  private String email;

  @Size(max = 255)
  @Column(length = 255)
  private String website;

  // Bank details for remittance/deposits
  @Size(max = 100)
  @Column(name = "bank_name", length = 100)
  private String bankName;

  @Size(max = 50)
  @Column(name = "bank_account_number", length = 50)
  private String bankAccountNumber;

  @Size(max = 50)
  @Column(name = "bank_routing", length = 50)
  private String bankRouting;

  // Tax override (e.g., zero-rated export customer)
  @Size(max = 10)
  @Column(name = "tax_override_code", length = 10)
  private String taxOverrideCode;

  // Default GL account for allocation
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "default_account_id")
  private Account defaultAccount;

  // Payment terms (e.g., "Net 30", "Due on receipt")
  @Size(max = 50)
  @Column(name = "payment_terms", length = 50)
  private String paymentTerms;

  // Credit limit (for AR management)
  @Column(name = "credit_limit", precision = 19, scale = 2)
  private BigDecimal creditLimit;

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
  public Contact() {}

  public Contact(Company company, String code, String name, ContactType type) {
    this.company = company;
    this.code = code;
    this.name = name;
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

  public ContactType getType() {
    return type;
  }

  public void setType(ContactType type) {
    this.type = type;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getColorTag() {
    return colorTag;
  }

  public void setColorTag(String colorTag) {
    this.colorTag = colorTag;
  }

  public String getAddressLine1() {
    return addressLine1;
  }

  public void setAddressLine1(String addressLine1) {
    this.addressLine1 = addressLine1;
  }

  public String getAddressLine2() {
    return addressLine2;
  }

  public void setAddressLine2(String addressLine2) {
    this.addressLine2 = addressLine2;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public void setPostalCode(String postalCode) {
    this.postalCode = postalCode;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getMobile() {
    return mobile;
  }

  public void setMobile(String mobile) {
    this.mobile = mobile;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getWebsite() {
    return website;
  }

  public void setWebsite(String website) {
    this.website = website;
  }

  public String getBankName() {
    return bankName;
  }

  public void setBankName(String bankName) {
    this.bankName = bankName;
  }

  public String getBankAccountNumber() {
    return bankAccountNumber;
  }

  public void setBankAccountNumber(String bankAccountNumber) {
    this.bankAccountNumber = bankAccountNumber;
  }

  public String getBankRouting() {
    return bankRouting;
  }

  public void setBankRouting(String bankRouting) {
    this.bankRouting = bankRouting;
  }

  public String getTaxOverrideCode() {
    return taxOverrideCode;
  }

  public void setTaxOverrideCode(String taxOverrideCode) {
    this.taxOverrideCode = taxOverrideCode;
  }

  public Account getDefaultAccount() {
    return defaultAccount;
  }

  public void setDefaultAccount(Account defaultAccount) {
    this.defaultAccount = defaultAccount;
  }

  public String getPaymentTerms() {
    return paymentTerms;
  }

  public void setPaymentTerms(String paymentTerms) {
    this.paymentTerms = paymentTerms;
  }

  public BigDecimal getCreditLimit() {
    return creditLimit;
  }

  public void setCreditLimit(BigDecimal creditLimit) {
    this.creditLimit = creditLimit;
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

  /** Returns a formatted address string. */
  public String getFormattedAddress() {
    StringBuilder sb = new StringBuilder();
    if (addressLine1 != null && !addressLine1.isBlank()) {
      sb.append(addressLine1);
    }
    if (addressLine2 != null && !addressLine2.isBlank()) {
      if (sb.length() > 0) sb.append(", ");
      sb.append(addressLine2);
    }
    if (city != null && !city.isBlank()) {
      if (sb.length() > 0) sb.append(", ");
      sb.append(city);
    }
    if (region != null && !region.isBlank()) {
      if (sb.length() > 0) sb.append(" ");
      sb.append(region);
    }
    if (postalCode != null && !postalCode.isBlank()) {
      if (sb.length() > 0) sb.append(" ");
      sb.append(postalCode);
    }
    return sb.toString();
  }
}
