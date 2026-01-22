package com.example.application.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Represents a General Ledger account in the Chart of Accounts.
 * Accounts are hierarchical (parent/child) and company-scoped.
 */
@Entity
@Table(name = "account", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"company_id", "code"})
})
public class Account {

    public enum AccountType {
        ASSET, LIABILITY, EQUITY, INCOME, EXPENSE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank
    @Size(max = 7)
    @Column(nullable = false, length = 7)
    private String code;

    @Size(max = 20)
    @Column(name = "alt_code", length = 20)
    private String altCode;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Account parent;

    @Column(nullable = false)
    private boolean active = true;

    @Size(max = 10)
    @Column(name = "tax_default_code", length = 10)
    private String taxDefaultCode;

    @Column(name = "security_level")
    private Integer securityLevel;

    // Bank account fields (optional, only used when isBankAccount = true)
    @Column(name = "is_bank_account", nullable = false)
    private boolean bankAccount = false;

    @Size(max = 100)
    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Size(max = 50)
    @Column(name = "bank_number", length = 50)
    private String bankNumber;

    @Size(max = 3)
    @Column(name = "bank_currency", length = 3)
    private String bankCurrency;

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
    public Account() {
    }

    public Account(Company company, String code, String name, AccountType type) {
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

    public String getAltCode() {
        return altCode;
    }

    public void setAltCode(String altCode) {
        this.altCode = altCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public Account getParent() {
        return parent;
    }

    public void setParent(Account parent) {
        this.parent = parent;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getTaxDefaultCode() {
        return taxDefaultCode;
    }

    public void setTaxDefaultCode(String taxDefaultCode) {
        this.taxDefaultCode = taxDefaultCode;
    }

    public Integer getSecurityLevel() {
        return securityLevel;
    }

    public void setSecurityLevel(Integer securityLevel) {
        this.securityLevel = securityLevel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(boolean bankAccount) {
        this.bankAccount = bankAccount;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBankNumber() {
        return bankNumber;
    }

    public void setBankNumber(String bankNumber) {
        this.bankNumber = bankNumber;
    }

    public String getBankCurrency() {
        return bankCurrency;
    }

    public void setBankCurrency(String bankCurrency) {
        this.bankCurrency = bankCurrency;
    }
}
