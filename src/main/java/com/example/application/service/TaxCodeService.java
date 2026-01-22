package com.example.application.service;

import com.example.application.domain.Company;
import com.example.application.domain.TaxCode;
import com.example.application.domain.User;
import com.example.application.repository.TaxCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for managing tax codes.
 * Tax codes define rates and types for GST/VAT calculations.
 */
@Service
@Transactional
public class TaxCodeService {

    private final TaxCodeRepository taxCodeRepository;
    private final AuditService auditService;

    public TaxCodeService(TaxCodeRepository taxCodeRepository, AuditService auditService) {
        this.taxCodeRepository = taxCodeRepository;
        this.auditService = auditService;
    }

    /**
     * Creates a new tax code for the given company.
     * @throws IllegalArgumentException if code already exists
     */
    public TaxCode createTaxCode(Company company, String code, String name,
                                  BigDecimal rate, TaxCode.TaxType type) {
        return createTaxCode(company, code, name, rate, type, null);
    }

    /**
     * Creates a new tax code for the given company with audit logging.
     * @throws IllegalArgumentException if code already exists
     */
    public TaxCode createTaxCode(Company company, String code, String name,
                                  BigDecimal rate, TaxCode.TaxType type, User actor) {
        if (taxCodeRepository.existsByCompanyAndCode(company, code)) {
            throw new IllegalArgumentException("Tax code already exists: " + code);
        }
        TaxCode taxCode = new TaxCode(company, code, name, rate, type);
        taxCode = taxCodeRepository.save(taxCode);

        auditService.logEvent(company, actor, "TAXCODE_CREATED", "TaxCode", taxCode.getId(),
            "Created tax code: " + code + " - " + name + " @ " + rate);

        return taxCode;
    }

    /**
     * Finds all tax codes for a company, ordered by code.
     */
    @Transactional(readOnly = true)
    public List<TaxCode> findByCompany(Company company) {
        return taxCodeRepository.findByCompanyOrderByCode(company);
    }

    /**
     * Finds active tax codes for a company, ordered by code.
     */
    @Transactional(readOnly = true)
    public List<TaxCode> findActiveByCompany(Company company) {
        return taxCodeRepository.findByCompanyAndActiveOrderByCode(company, true);
    }

    /**
     * Finds a tax code by company and code.
     */
    @Transactional(readOnly = true)
    public Optional<TaxCode> findByCompanyAndCode(Company company, String code) {
        return taxCodeRepository.findByCompanyAndCode(company, code);
    }

    /**
     * Finds a tax code by ID.
     */
    @Transactional(readOnly = true)
    public Optional<TaxCode> findById(Long id) {
        return taxCodeRepository.findById(id);
    }

    /**
     * Saves a tax code.
     */
    public TaxCode save(TaxCode taxCode) {
        return save(taxCode, null);
    }

    /**
     * Saves a tax code with audit logging for edits.
     * Captures before/after state for key fields.
     *
     * @param taxCode the tax code to save
     * @param actor the user making the change
     * @return the saved tax code
     */
    public TaxCode save(TaxCode taxCode, User actor) {
        boolean isNew = taxCode.getId() == null;

        if (!isNew) {
            // Capture before state for existing tax code
            TaxCode before = taxCodeRepository.findById(taxCode.getId()).orElse(null);
            if (before != null) {
                Map<String, Object> changes = new LinkedHashMap<>();
                if (!before.getCode().equals(taxCode.getCode())) {
                    changes.put("code", Map.of("from", before.getCode(), "to", taxCode.getCode()));
                }
                if (!before.getName().equals(taxCode.getName())) {
                    changes.put("name", Map.of("from", before.getName(), "to", taxCode.getName()));
                }
                if (before.getRate().compareTo(taxCode.getRate()) != 0) {
                    changes.put("rate", Map.of("from", before.getRate().toString(), "to", taxCode.getRate().toString()));
                }
                if (before.getType() != taxCode.getType()) {
                    changes.put("type", Map.of("from", before.getType().name(), "to", taxCode.getType().name()));
                }
                if (before.isActive() != taxCode.isActive()) {
                    changes.put("active", Map.of("from", before.isActive(), "to", taxCode.isActive()));
                }

                if (!changes.isEmpty()) {
                    TaxCode saved = taxCodeRepository.save(taxCode);
                    auditService.logEvent(taxCode.getCompany(), actor, "TAXCODE_UPDATED", "TaxCode", taxCode.getId(),
                        "Updated tax code: " + taxCode.getCode(), changes);
                    return saved;
                }
            }
        }

        return taxCodeRepository.save(taxCode);
    }

    /**
     * Deactivates a tax code (soft delete).
     */
    public void deactivate(TaxCode taxCode) {
        deactivate(taxCode, null);
    }

    /**
     * Deactivates a tax code with audit logging.
     *
     * @param taxCode the tax code to deactivate
     * @param actor the user making the change
     */
    public void deactivate(TaxCode taxCode, User actor) {
        taxCode.setActive(false);
        taxCodeRepository.save(taxCode);

        auditService.logEvent(taxCode.getCompany(), actor, "TAXCODE_DEACTIVATED", "TaxCode", taxCode.getId(),
            "Deactivated tax code: " + taxCode.getCode());
    }

    /**
     * Creates default NZ GST tax codes for a new company.
     * Called during company setup to provide sensible defaults.
     */
    public void createDefaultTaxCodes(Company company) {
        // Standard GST 15%
        createTaxCode(company, "GST", "GST 15%",
            new BigDecimal("0.15"), TaxCode.TaxType.STANDARD);

        // Zero-rated
        createTaxCode(company, "ZERO", "Zero Rated",
            BigDecimal.ZERO, TaxCode.TaxType.ZERO_RATED);

        // Exempt
        createTaxCode(company, "EXEMPT", "Exempt",
            BigDecimal.ZERO, TaxCode.TaxType.EXEMPT);

        // Out of scope (e.g., wages, non-business)
        createTaxCode(company, "N/A", "No GST",
            BigDecimal.ZERO, TaxCode.TaxType.OUT_OF_SCOPE);
    }
}
