package com.example.application.service;

import com.example.application.domain.Company;
import com.example.application.domain.FiscalYear;
import com.example.application.domain.Period;
import com.example.application.repository.CompanyRepository;
import com.example.application.repository.FiscalYearRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final FiscalYearRepository fiscalYearRepository;

    public CompanyService(CompanyRepository companyRepository,
                          FiscalYearRepository fiscalYearRepository) {
        this.companyRepository = companyRepository;
        this.fiscalYearRepository = fiscalYearRepository;
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
}
