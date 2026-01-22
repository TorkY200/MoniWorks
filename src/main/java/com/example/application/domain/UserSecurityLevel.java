package com.example.application.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Stores the maximum account security level a user can access within a company.
 * Accounts with security_level greater than the user's max_level will be hidden
 * from that user in reports, grids, and drilldowns.
 *
 * Security levels work as follows:
 * - Level 0 (default): Basic accounts (most accounts)
 * - Level 1+: Restricted accounts (e.g., payroll, owner's drawings)
 *
 * A user with max_level=0 can only see accounts where security_level is null or 0.
 * A user with max_level=1 can see accounts where security_level is null, 0, or 1.
 * And so on.
 *
 * If a user has no UserSecurityLevel record for a company, they default to level 0.
 */
@Entity
@Table(name = "user_security_level", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "company_id"})
})
public class UserSecurityLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotNull
    @Column(name = "max_level", nullable = false)
    private Integer maxLevel = 0;

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
    public UserSecurityLevel() {
    }

    public UserSecurityLevel(User user, Company company, Integer maxLevel) {
        this.user = user;
        this.company = company;
        this.maxLevel = maxLevel;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Integer getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(Integer maxLevel) {
        this.maxLevel = maxLevel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
