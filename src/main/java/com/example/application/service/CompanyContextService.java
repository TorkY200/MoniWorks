package com.example.application.service;

import com.example.application.domain.Company;
import com.example.application.domain.User;
import com.example.application.repository.UserRepository;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Session-scoped service that manages the current company context.
 * For Release 1 (single company support), this auto-selects or creates the first company.
 * Future releases will support company switching via UI.
 */
@Service
@VaadinSessionScope
public class CompanyContextService {

    private final CompanyService companyService;
    private final UserRepository userRepository;
    private Company currentCompany;
    private User currentUser;

    public CompanyContextService(CompanyService companyService, UserRepository userRepository) {
        this.companyService = companyService;
        this.userRepository = userRepository;
    }

    /**
     * Gets the current company for this session.
     * If no company exists, creates a default one.
     * @return the current company
     */
    public Company getCurrentCompany() {
        if (currentCompany == null) {
            currentCompany = companyService.findAll().stream()
                .findFirst()
                .orElseGet(this::createDefaultCompany);
        }
        return currentCompany;
    }

    /**
     * Sets the current company for this session.
     * @param company the company to set as current
     */
    public void setCurrentCompany(Company company) {
        this.currentCompany = company;
    }

    /**
     * Returns the ID of the current company.
     * @return company ID
     */
    public Long getCurrentCompanyId() {
        return getCurrentCompany().getId();
    }

    /**
     * Creates a default company for first-time setup.
     * Uses sensible defaults for NZ-based business (per spec).
     */
    private Company createDefaultCompany() {
        // Default to NZ company with April 1 fiscal year start (common in NZ)
        LocalDate fiscalYearStart = LocalDate.of(LocalDate.now().getYear(), 4, 1);
        if (LocalDate.now().isBefore(fiscalYearStart)) {
            fiscalYearStart = fiscalYearStart.minusYears(1);
        }
        return companyService.createCompany(
            "My Company",
            "NZ",
            "NZD",
            fiscalYearStart
        );
    }

    /**
     * Refreshes the current company from the database.
     * Call this after the company has been modified.
     */
    public void refresh() {
        if (currentCompany != null) {
            currentCompany = companyService.findById(currentCompany.getId())
                .orElse(null);
        }
    }

    /**
     * Gets the current authenticated user.
     * @return the current user, or null if not authenticated
     */
    public User getCurrentUser() {
        if (currentUser == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                currentUser = userRepository.findByEmail(auth.getName()).orElse(null);
            }
        }
        return currentUser;
    }

    /**
     * Clears the current user cache.
     * Call this when the user context may have changed.
     */
    public void clearUserCache() {
        currentUser = null;
    }
}
