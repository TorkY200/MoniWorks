package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.repository.KPIRepository;
import com.example.application.repository.KPIValueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing KPIs and KPI values.
 * KPIs track off-ledger metrics for dashboard and reporting purposes.
 */
@Service
@Transactional
public class KPIService {

    private final KPIRepository kpiRepository;
    private final KPIValueRepository kpiValueRepository;
    private final AuditService auditService;

    public KPIService(KPIRepository kpiRepository,
                      KPIValueRepository kpiValueRepository,
                      AuditService auditService) {
        this.kpiRepository = kpiRepository;
        this.kpiValueRepository = kpiValueRepository;
        this.auditService = auditService;
    }

    /**
     * Creates a new KPI for the given company.
     * @throws IllegalArgumentException if code already exists
     */
    public KPI createKPI(Company company, String code, String name, String unit, User actor) {
        if (kpiRepository.existsByCompanyAndCode(company, code)) {
            throw new IllegalArgumentException("KPI code already exists: " + code);
        }
        KPI kpi = new KPI(company, code, name, unit);
        kpi = kpiRepository.save(kpi);

        auditService.logEvent(company, actor, "KPI_CREATE", "KPI",
            kpi.getId(), "Created KPI: " + code + " - " + name);

        return kpi;
    }

    /**
     * Finds all KPIs for a company.
     */
    @Transactional(readOnly = true)
    public List<KPI> findByCompany(Company company) {
        return kpiRepository.findByCompanyOrderByCode(company);
    }

    /**
     * Finds active KPIs for a company.
     */
    @Transactional(readOnly = true)
    public List<KPI> findActiveByCompany(Company company) {
        return kpiRepository.findByCompanyAndActiveOrderByCode(company, true);
    }

    /**
     * Finds a KPI by company and code.
     */
    @Transactional(readOnly = true)
    public Optional<KPI> findByCompanyAndCode(Company company, String code) {
        return kpiRepository.findByCompanyAndCode(company, code);
    }

    /**
     * Finds a KPI by ID.
     */
    @Transactional(readOnly = true)
    public Optional<KPI> findById(Long id) {
        return kpiRepository.findById(id);
    }

    /**
     * Saves a KPI.
     */
    public KPI save(KPI kpi, User actor) {
        boolean isNew = kpi.getId() == null;
        KPI saved = kpiRepository.save(kpi);

        if (!isNew) {
            auditService.logEvent(kpi.getCompany(), actor, "KPI_UPDATE",
                "KPI", saved.getId(),
                "Updated KPI: " + saved.getCode() + " - " + saved.getName(),
                Map.of("code", saved.getCode(), "name", saved.getName(),
                       "unit", saved.getUnit() != null ? saved.getUnit() : "",
                       "active", saved.isActive()));
        }

        return saved;
    }

    /**
     * Deactivates a KPI.
     */
    public void deactivate(KPI kpi, User actor) {
        kpi.setActive(false);
        kpiRepository.save(kpi);

        auditService.logEvent(kpi.getCompany(), actor, "KPI_DEACTIVATE",
            "KPI", kpi.getId(),
            "Deactivated KPI: " + kpi.getCode());
    }

    // KPI Value operations

    /**
     * Sets or updates a KPI value for a period.
     */
    public KPIValue setKPIValue(KPI kpi, Period period, BigDecimal value, String notes) {
        Optional<KPIValue> existing = kpiValueRepository.findByKpiAndPeriod(kpi, period);

        KPIValue kpiValue;
        if (existing.isPresent()) {
            kpiValue = existing.get();
            kpiValue.setValue(value);
            kpiValue.setNotes(notes);
        } else {
            kpiValue = new KPIValue(kpi, period, value);
            kpiValue.setNotes(notes);
        }

        return kpiValueRepository.save(kpiValue);
    }

    /**
     * Gets a KPI value for a specific period.
     */
    @Transactional(readOnly = true)
    public Optional<KPIValue> getKPIValue(KPI kpi, Period period) {
        return kpiValueRepository.findByKpiAndPeriod(kpi, period);
    }

    /**
     * Gets all values for a KPI.
     */
    @Transactional(readOnly = true)
    public List<KPIValue> getValuesForKPI(KPI kpi) {
        return kpiValueRepository.findByKpiOrderByPeriodStartDateDesc(kpi);
    }

    /**
     * Gets values for a KPI in a fiscal year.
     */
    @Transactional(readOnly = true)
    public List<KPIValue> getValuesForKPIAndFiscalYear(KPI kpi, Long fiscalYearId) {
        return kpiValueRepository.findByKpiAndFiscalYear(kpi, fiscalYearId);
    }

    /**
     * Gets all KPI values for a company in a fiscal year.
     */
    @Transactional(readOnly = true)
    public List<KPIValue> getValuesForCompanyAndFiscalYear(Long companyId, Long fiscalYearId) {
        return kpiValueRepository.findByCompanyAndFiscalYear(companyId, fiscalYearId);
    }

    /**
     * Deletes a KPI value.
     */
    public void deleteKPIValue(KPIValue value) {
        kpiValueRepository.delete(value);
    }

    /**
     * Deletes all values for a KPI.
     */
    public void deleteAllValuesForKPI(KPI kpi, User actor) {
        kpiValueRepository.deleteByKpi(kpi);

        auditService.logEvent(kpi.getCompany(), actor, "KPI_VALUES_CLEARED",
            "KPI", kpi.getId(),
            "Cleared all values from KPI: " + kpi.getCode());
    }

    /**
     * Creates default KPIs for a new company.
     * Called during company setup to provide sensible defaults.
     */
    public void createDefaultKPIs(Company company, User actor) {
        createKPI(company, "REV", "Monthly Revenue", "$", actor);
        createKPI(company, "CUST", "Active Customers", "#", actor);
        createKPI(company, "NPS", "Net Promoter Score", "score", actor);
    }
}
