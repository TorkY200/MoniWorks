package com.example.application.service;

import com.example.application.domain.Company;
import com.example.application.domain.CompanySettings;
import com.example.application.domain.FiscalYear;
import com.example.application.domain.PdfSettings;
import com.example.application.domain.Period;
import com.example.application.repository.CompanyRepository;
import com.example.application.repository.FiscalYearRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final CompanyRepository companyRepository;
    private final FiscalYearRepository fiscalYearRepository;
    private final ObjectMapper objectMapper;

    public CompanyService(CompanyRepository companyRepository,
                          FiscalYearRepository fiscalYearRepository) {
        this.companyRepository = companyRepository;
        this.fiscalYearRepository = fiscalYearRepository;
        this.objectMapper = new ObjectMapper();
    }

    public Company createCompany(String name, String country, String baseCurrency,
                                  LocalDate fiscalYearStart) {
        Company company = new Company(name, country, baseCurrency, fiscalYearStart);
        company = companyRepository.save(company);

        // Create initial fiscal year
        createInitialFiscalYear(company);

        return company;
    }

    private void createInitialFiscalYear(Company company) {
        LocalDate startDate = company.getFiscalYearStart();
        LocalDate endDate = startDate.plusYears(1).minusDays(1);
        String label = startDate.getYear() + "-" + endDate.getYear();

        FiscalYear fiscalYear = new FiscalYear(company, startDate, endDate, label);

        // Create 12 monthly periods
        LocalDate periodStart = startDate;
        for (int i = 1; i <= 12; i++) {
            LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);
            if (periodEnd.isAfter(endDate)) {
                periodEnd = endDate;
            }
            Period period = new Period(fiscalYear, i, periodStart, periodEnd);
            fiscalYear.addPeriod(period);
            periodStart = periodEnd.plusDays(1);
        }

        fiscalYearRepository.save(fiscalYear);
    }

    @Transactional(readOnly = true)
    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Company> findById(Long id) {
        return companyRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Company> findByName(String name) {
        return companyRepository.findByName(name);
    }

    public Company save(Company company) {
        return companyRepository.save(company);
    }

    public void delete(Company company) {
        companyRepository.delete(company);
    }

    /**
     * Gets the parsed company settings from settingsJson field.
     * Returns default settings if none are configured.
     */
    @Transactional(readOnly = true)
    public CompanySettings getSettings(Company company) {
        String json = company.getSettingsJson();
        if (json == null || json.isBlank()) {
            return new CompanySettings();
        }
        try {
            return objectMapper.readValue(json, CompanySettings.class);
        } catch (Exception e) {
            log.warn("Failed to parse company settings JSON for company {}: {}",
                company.getId(), e.getMessage());
            return new CompanySettings();
        }
    }

    /**
     * Saves company settings to the settingsJson field.
     */
    public Company saveSettings(Company company, CompanySettings settings) {
        try {
            String json = objectMapper.writeValueAsString(settings);
            company.setSettingsJson(json);
            return companyRepository.save(company);
        } catch (Exception e) {
            log.error("Failed to serialize company settings for company {}: {}",
                company.getId(), e.getMessage());
            throw new RuntimeException("Failed to save company settings", e);
        }
    }

    /**
     * Gets PDF settings for a company.
     * Returns default settings if none are configured.
     */
    @Transactional(readOnly = true)
    public PdfSettings getPdfSettings(Company company) {
        return getSettings(company).getOrCreatePdfSettings();
    }

    /**
     * Saves PDF settings for a company.
     */
    public Company savePdfSettings(Company company, PdfSettings pdfSettings) {
        CompanySettings settings = getSettings(company);
        settings.setPdfSettings(pdfSettings);
        return saveSettings(company, settings);
    }
}
