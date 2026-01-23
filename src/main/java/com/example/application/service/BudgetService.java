package com.example.application.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.*;
import com.example.application.repository.BudgetLineRepository;
import com.example.application.repository.BudgetRepository;

/**
 * Service for managing budgets and budget lines. Supports budget-vs-actual reporting by account,
 * period, and department.
 */
@Service
@Transactional
public class BudgetService {

  private final BudgetRepository budgetRepository;
  private final BudgetLineRepository budgetLineRepository;
  private final AuditService auditService;

  public BudgetService(
      BudgetRepository budgetRepository,
      BudgetLineRepository budgetLineRepository,
      AuditService auditService) {
    this.budgetRepository = budgetRepository;
    this.budgetLineRepository = budgetLineRepository;
    this.auditService = auditService;
  }

  /**
   * Creates a new budget for the given company.
   *
   * @throws IllegalArgumentException if name already exists
   */
  public Budget createBudget(Company company, String name, Budget.BudgetType type, User actor) {
    if (budgetRepository.existsByCompanyAndName(company, name)) {
      throw new IllegalArgumentException("Budget name already exists: " + name);
    }
    Budget budget = new Budget(company, name, type);
    budget = budgetRepository.save(budget);

    auditService.logEvent(
        company,
        actor,
        "BUDGET_CREATE",
        "Budget",
        budget.getId(),
        "Created budget: " + name + " (Type " + type + ")");

    return budget;
  }

  /** Finds all budgets for a company. */
  @Transactional(readOnly = true)
  public List<Budget> findByCompany(Company company) {
    return budgetRepository.findByCompanyOrderByName(company);
  }

  /** Finds active budgets for a company. */
  @Transactional(readOnly = true)
  public List<Budget> findActiveByCompany(Company company) {
    return budgetRepository.findByCompanyAndActiveOrderByName(company, true);
  }

  /** Finds budgets of a specific type for a company. */
  @Transactional(readOnly = true)
  public List<Budget> findByCompanyAndType(Company company, Budget.BudgetType type) {
    return budgetRepository.findByCompanyAndTypeOrderByName(company, type);
  }

  /** Finds a budget by ID. */
  @Transactional(readOnly = true)
  public Optional<Budget> findById(Long id) {
    return budgetRepository.findById(id);
  }

  /** Saves a budget. */
  public Budget save(Budget budget, User actor) {
    boolean isNew = budget.getId() == null;
    Budget saved = budgetRepository.save(budget);

    if (!isNew) {
      auditService.logEvent(
          budget.getCompany(),
          actor,
          "BUDGET_UPDATE",
          "Budget",
          saved.getId(),
          "Updated budget: " + saved.getName(),
          Map.of("name", saved.getName(), "active", saved.isActive()));
    }

    return saved;
  }

  /** Deactivates a budget. */
  public void deactivate(Budget budget, User actor) {
    budget.setActive(false);
    budgetRepository.save(budget);

    auditService.logEvent(
        budget.getCompany(),
        actor,
        "BUDGET_DEACTIVATE",
        "Budget",
        budget.getId(),
        "Deactivated budget: " + budget.getName());
  }

  // Budget Line operations

  /** Adds or updates a budget line. */
  public BudgetLine saveBudgetLine(
      Budget budget, Period period, Account account, Department department, BigDecimal amount) {
    Optional<BudgetLine> existing =
        budgetLineRepository.findByBudgetAndPeriodAndAccountAndDepartment(
            budget, period, account, department);

    BudgetLine line;
    if (existing.isPresent()) {
      line = existing.get();
      line.setAmount(amount);
    } else {
      line = new BudgetLine(budget, period, account, department, amount);
    }

    return budgetLineRepository.save(line);
  }

  /** Finds budget lines for a budget. */
  @Transactional(readOnly = true)
  public List<BudgetLine> findLinesByBudget(Budget budget) {
    return budgetLineRepository.findByBudget(budget);
  }

  /** Finds budget lines for a budget and period. */
  @Transactional(readOnly = true)
  public List<BudgetLine> findLinesByBudgetAndPeriod(Budget budget, Period period) {
    return budgetLineRepository.findByBudgetAndPeriod(budget, period);
  }

  /** Finds budget lines for a budget and fiscal year. */
  @Transactional(readOnly = true)
  public List<BudgetLine> findLinesByBudgetAndFiscalYear(Budget budget, Long fiscalYearId) {
    return budgetLineRepository.findByBudgetAndFiscalYear(budget, fiscalYearId);
  }

  /** Gets the total budget amount for an account in a fiscal year. */
  @Transactional(readOnly = true)
  public BigDecimal getBudgetAmountForAccount(Budget budget, Account account, Long fiscalYearId) {
    BigDecimal sum =
        budgetLineRepository.sumByBudgetAndAccountAndFiscalYear(budget, account, fiscalYearId);
    return sum != null ? sum : BigDecimal.ZERO;
  }

  /** Gets the total budget amount for an account and department in a fiscal year. */
  @Transactional(readOnly = true)
  public BigDecimal getBudgetAmountForAccountAndDepartment(
      Budget budget, Account account, Department department, Long fiscalYearId) {
    BigDecimal sum =
        budgetLineRepository.sumByBudgetAndAccountAndDepartmentAndFiscalYear(
            budget, account, department, fiscalYearId);
    return sum != null ? sum : BigDecimal.ZERO;
  }

  /** Deletes a budget line. */
  public void deleteBudgetLine(BudgetLine line) {
    budgetLineRepository.delete(line);
  }

  /** Deletes all budget lines for a budget. */
  public void deleteAllLinesForBudget(Budget budget, User actor) {
    budgetLineRepository.deleteByBudget(budget);

    auditService.logEvent(
        budget.getCompany(),
        actor,
        "BUDGET_LINES_CLEARED",
        "Budget",
        budget.getId(),
        "Cleared all lines from budget: " + budget.getName());
  }
}
