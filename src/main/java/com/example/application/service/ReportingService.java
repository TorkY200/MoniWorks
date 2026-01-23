package com.example.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.*;
import com.example.application.repository.*;

/**
 * Service for generating financial reports. Produces Trial Balance, P&L, and Balance Sheet from
 * ledger entries.
 */
@Service
@Transactional(readOnly = true)
public class ReportingService {

  private final AccountRepository accountRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final BudgetLineRepository budgetLineRepository;
  private final PeriodRepository periodRepository;
  private final SalesInvoiceRepository salesInvoiceRepository;
  private final SupplierBillRepository supplierBillRepository;

  public ReportingService(
      AccountRepository accountRepository,
      LedgerEntryRepository ledgerEntryRepository,
      BudgetLineRepository budgetLineRepository,
      PeriodRepository periodRepository,
      SalesInvoiceRepository salesInvoiceRepository,
      SupplierBillRepository supplierBillRepository) {
    this.accountRepository = accountRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.budgetLineRepository = budgetLineRepository;
    this.periodRepository = periodRepository;
    this.salesInvoiceRepository = salesInvoiceRepository;
    this.supplierBillRepository = supplierBillRepository;
  }

  /**
   * Generates a Trial Balance for the given company and date range. Returns a map of account to
   * balance (debits positive, credits negative).
   */
  public TrialBalance generateTrialBalance(
      Company company, LocalDate startDate, LocalDate endDate) {
    return generateTrialBalance(company, startDate, endDate, Integer.MAX_VALUE);
  }

  /**
   * Generates a Trial Balance with security level filtering. Accounts with securityLevel >
   * maxSecurityLevel are excluded.
   *
   * @param company the company
   * @param startDate start of reporting period
   * @param endDate end of reporting period
   * @param maxSecurityLevel the user's maximum security level
   * @return the trial balance with restricted accounts filtered out
   */
  public TrialBalance generateTrialBalance(
      Company company, LocalDate startDate, LocalDate endDate, int maxSecurityLevel) {
    List<Account> accounts =
        accountRepository.findByCompanyWithSecurityLevel(company, maxSecurityLevel);
    List<TrialBalanceLine> lines = new ArrayList<>();

    BigDecimal totalDebits = BigDecimal.ZERO;
    BigDecimal totalCredits = BigDecimal.ZERO;

    for (Account account : accounts) {
      BigDecimal debits = ledgerEntryRepository.sumDebitsByAccountAsOf(account, endDate);
      BigDecimal credits = ledgerEntryRepository.sumCreditsByAccountAsOf(account, endDate);

      if (debits == null) debits = BigDecimal.ZERO;
      if (credits == null) credits = BigDecimal.ZERO;

      // Skip accounts with no activity
      if (debits.compareTo(BigDecimal.ZERO) == 0 && credits.compareTo(BigDecimal.ZERO) == 0) {
        continue;
      }

      lines.add(new TrialBalanceLine(account, debits, credits));
      totalDebits = totalDebits.add(debits);
      totalCredits = totalCredits.add(credits);
    }

    return new TrialBalance(startDate, endDate, lines, totalDebits, totalCredits);
  }

  /** Generates a Profit & Loss statement for the given date range. */
  public ProfitAndLoss generateProfitAndLoss(
      Company company, LocalDate startDate, LocalDate endDate) {
    return generateProfitAndLoss(company, startDate, endDate, null, Integer.MAX_VALUE);
  }

  /**
   * Generates a Profit & Loss statement with security level filtering.
   *
   * @param company the company
   * @param startDate start of reporting period
   * @param endDate end of reporting period
   * @param maxSecurityLevel the user's maximum security level
   * @return the P&L with restricted accounts filtered out
   */
  public ProfitAndLoss generateProfitAndLoss(
      Company company, LocalDate startDate, LocalDate endDate, int maxSecurityLevel) {
    return generateProfitAndLoss(company, startDate, endDate, null, maxSecurityLevel);
  }

  private ProfitAndLoss generateProfitAndLossInternal(
      Company company,
      LocalDate startDate,
      LocalDate endDate,
      Department department,
      int maxSecurityLevel) {
    List<Account> incomeAccounts =
        accountRepository.findByCompanyIdAndTypeWithSecurityLevel(
            company.getId(), Account.AccountType.INCOME, maxSecurityLevel);
    List<Account> expenseAccounts =
        accountRepository.findByCompanyIdAndTypeWithSecurityLevel(
            company.getId(), Account.AccountType.EXPENSE, maxSecurityLevel);

    List<ProfitAndLossLine> incomeLines = new ArrayList<>();
    List<ProfitAndLossLine> expenseLines = new ArrayList<>();

    BigDecimal totalIncome = BigDecimal.ZERO;
    BigDecimal totalExpenses = BigDecimal.ZERO;

    // Calculate income (credits - debits)
    for (Account account : incomeAccounts) {
      List<LedgerEntry> entries;
      if (department != null) {
        entries =
            ledgerEntryRepository.findByAccountAndDateRangeAndDepartment(
                account, startDate, endDate, department);
      } else {
        entries = ledgerEntryRepository.findByAccountAndDateRange(account, startDate, endDate);
      }

      BigDecimal amount = BigDecimal.ZERO;
      for (LedgerEntry entry : entries) {
        amount = amount.add(entry.getAmountCr()).subtract(entry.getAmountDr());
      }

      if (amount.compareTo(BigDecimal.ZERO) != 0) {
        incomeLines.add(new ProfitAndLossLine(account, amount));
        totalIncome = totalIncome.add(amount);
      }
    }

    // Calculate expenses (debits - credits)
    for (Account account : expenseAccounts) {
      List<LedgerEntry> entries;
      if (department != null) {
        entries =
            ledgerEntryRepository.findByAccountAndDateRangeAndDepartment(
                account, startDate, endDate, department);
      } else {
        entries = ledgerEntryRepository.findByAccountAndDateRange(account, startDate, endDate);
      }

      BigDecimal amount = BigDecimal.ZERO;
      for (LedgerEntry entry : entries) {
        amount = amount.add(entry.getAmountDr()).subtract(entry.getAmountCr());
      }

      if (amount.compareTo(BigDecimal.ZERO) != 0) {
        expenseLines.add(new ProfitAndLossLine(account, amount));
        totalExpenses = totalExpenses.add(amount);
      }
    }

    BigDecimal netProfit = totalIncome.subtract(totalExpenses);

    return new ProfitAndLoss(
        startDate,
        endDate,
        department,
        incomeLines,
        expenseLines,
        totalIncome,
        totalExpenses,
        netProfit);
  }

  /**
   * Generates a Profit & Loss statement for the given date range, filtered by department. If
   * department is null, all entries are included (same as non-filtered version).
   */
  public ProfitAndLoss generateProfitAndLoss(
      Company company, LocalDate startDate, LocalDate endDate, Department department) {
    return generateProfitAndLoss(company, startDate, endDate, department, Integer.MAX_VALUE);
  }

  /**
   * Generates a Profit & Loss statement with department and security level filtering.
   *
   * @param company the company
   * @param startDate start of reporting period
   * @param endDate end of reporting period
   * @param department optional department filter (null = all)
   * @param maxSecurityLevel the user's maximum security level
   * @return the P&L with restricted accounts filtered out
   */
  public ProfitAndLoss generateProfitAndLoss(
      Company company,
      LocalDate startDate,
      LocalDate endDate,
      Department department,
      int maxSecurityLevel) {
    return generateProfitAndLossInternal(company, startDate, endDate, department, maxSecurityLevel);
  }

  /**
   * Generates a Budget vs Actual report for the given budget and date range. Compares budgeted
   * amounts against actual ledger entries. If department is specified, filters both budget lines
   * and actual entries by department.
   */
  public BudgetVsActual generateBudgetVsActual(
      Company company,
      Budget budget,
      LocalDate startDate,
      LocalDate endDate,
      Department department) {
    return generateBudgetVsActual(
        company, budget, startDate, endDate, department, Integer.MAX_VALUE);
  }

  /**
   * Generates a Budget vs Actual report with security level filtering.
   *
   * @param company the company
   * @param budget the budget to compare against
   * @param startDate start of reporting period
   * @param endDate end of reporting period
   * @param department optional department filter (null = all)
   * @param maxSecurityLevel the user's maximum security level
   * @return the budget vs actual with restricted accounts filtered out
   */
  public BudgetVsActual generateBudgetVsActual(
      Company company,
      Budget budget,
      LocalDate startDate,
      LocalDate endDate,
      Department department,
      int maxSecurityLevel) {
    // Get all income and expense accounts that could have budget lines, filtered by security level
    List<Account> incomeAccounts =
        accountRepository.findByCompanyIdAndTypeWithSecurityLevel(
            company.getId(), Account.AccountType.INCOME, maxSecurityLevel);
    List<Account> expenseAccounts =
        accountRepository.findByCompanyIdAndTypeWithSecurityLevel(
            company.getId(), Account.AccountType.EXPENSE, maxSecurityLevel);

    // Get periods that fall within the date range
    List<Period> periods =
        periodRepository.findByFiscalYearCompanyAndDateRangeOverlap(company, startDate, endDate);

    List<BudgetVsActualLine> lines = new ArrayList<>();

    // Process income accounts
    for (Account account : incomeAccounts) {
      BudgetVsActualLine line =
          calculateBudgetVsActualLine(
              account, budget, periods, startDate, endDate, department, false);
      if (line.budgetAmount().compareTo(BigDecimal.ZERO) != 0
          || line.actualAmount().compareTo(BigDecimal.ZERO) != 0) {
        lines.add(line);
      }
    }

    // Process expense accounts
    for (Account account : expenseAccounts) {
      BudgetVsActualLine line =
          calculateBudgetVsActualLine(
              account, budget, periods, startDate, endDate, department, true);
      if (line.budgetAmount().compareTo(BigDecimal.ZERO) != 0
          || line.actualAmount().compareTo(BigDecimal.ZERO) != 0) {
        lines.add(line);
      }
    }

    // Calculate totals
    BigDecimal totalBudget =
        lines.stream()
            .map(BudgetVsActualLine::budgetAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalActual =
        lines.stream()
            .map(BudgetVsActualLine::actualAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalVariance = totalActual.subtract(totalBudget);
    BigDecimal totalVariancePercent =
        totalBudget.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : totalVariance
                .multiply(BigDecimal.valueOf(100))
                .divide(totalBudget, 2, RoundingMode.HALF_UP);

    return new BudgetVsActual(
        startDate,
        endDate,
        budget,
        department,
        lines,
        totalBudget,
        totalActual,
        totalVariance,
        totalVariancePercent);
  }

  private BudgetVsActualLine calculateBudgetVsActualLine(
      Account account,
      Budget budget,
      List<Period> periods,
      LocalDate startDate,
      LocalDate endDate,
      Department department,
      boolean isExpense) {

    BigDecimal budgetAmount = BigDecimal.ZERO;
    BigDecimal actualAmount = BigDecimal.ZERO;

    // Sum budget amounts for all periods in range
    for (Period period : periods) {
      List<BudgetLine> budgetLines;
      if (department != null) {
        budgetLineRepository
            .findByBudgetAndPeriodAndAccountAndDepartment(budget, period, account, department)
            .ifPresent(bl -> {});
        // Need to get budget lines for specific department
        budgetLines =
            budgetLineRepository.findByBudgetAndPeriod(budget, period).stream()
                .filter(bl -> bl.getAccount().equals(account))
                .filter(bl -> bl.getDepartment() != null && bl.getDepartment().equals(department))
                .toList();
      } else {
        budgetLines =
            budgetLineRepository.findByBudgetAndPeriod(budget, period).stream()
                .filter(bl -> bl.getAccount().equals(account))
                .toList();
      }

      for (BudgetLine bl : budgetLines) {
        budgetAmount = budgetAmount.add(bl.getAmount());
      }
    }

    // Calculate actual amount from ledger entries
    if (department != null) {
      List<LedgerEntry> entries =
          ledgerEntryRepository.findByAccountAndDateRangeAndDepartment(
              account, startDate, endDate, department);
      for (LedgerEntry entry : entries) {
        if (isExpense) {
          actualAmount = actualAmount.add(entry.getAmountDr()).subtract(entry.getAmountCr());
        } else {
          actualAmount = actualAmount.add(entry.getAmountCr()).subtract(entry.getAmountDr());
        }
      }
    } else {
      List<LedgerEntry> entries =
          ledgerEntryRepository.findByAccountAndDateRange(account, startDate, endDate);
      for (LedgerEntry entry : entries) {
        if (isExpense) {
          actualAmount = actualAmount.add(entry.getAmountDr()).subtract(entry.getAmountCr());
        } else {
          actualAmount = actualAmount.add(entry.getAmountCr()).subtract(entry.getAmountDr());
        }
      }
    }

    BigDecimal variance = actualAmount.subtract(budgetAmount);
    BigDecimal variancePercent =
        budgetAmount.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : variance
                .multiply(BigDecimal.valueOf(100))
                .divide(budgetAmount, 2, RoundingMode.HALF_UP);

    return new BudgetVsActualLine(account, budgetAmount, actualAmount, variance, variancePercent);
  }

  /** Generates a Balance Sheet as of the given date. */
  public BalanceSheet generateBalanceSheet(Company company, LocalDate asOfDate) {
    return generateBalanceSheet(company, asOfDate, Integer.MAX_VALUE);
  }

  /**
   * Generates a Balance Sheet with security level filtering.
   *
   * @param company the company
   * @param asOfDate the reporting date
   * @param maxSecurityLevel the user's maximum security level
   * @return the balance sheet with restricted accounts filtered out
   */
  public BalanceSheet generateBalanceSheet(
      Company company, LocalDate asOfDate, int maxSecurityLevel) {
    List<Account> assetAccounts =
        accountRepository.findByCompanyIdAndTypeWithSecurityLevel(
            company.getId(), Account.AccountType.ASSET, maxSecurityLevel);
    List<Account> liabilityAccounts =
        accountRepository.findByCompanyIdAndTypeWithSecurityLevel(
            company.getId(), Account.AccountType.LIABILITY, maxSecurityLevel);
    List<Account> equityAccounts =
        accountRepository.findByCompanyIdAndTypeWithSecurityLevel(
            company.getId(), Account.AccountType.EQUITY, maxSecurityLevel);

    List<BalanceSheetLine> assetLines = calculateBalances(assetAccounts, asOfDate, true);
    List<BalanceSheetLine> liabilityLines = calculateBalances(liabilityAccounts, asOfDate, false);
    List<BalanceSheetLine> equityLines = calculateBalances(equityAccounts, asOfDate, false);

    BigDecimal totalAssets =
        assetLines.stream().map(BalanceSheetLine::balance).reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalLiabilities =
        liabilityLines.stream()
            .map(BalanceSheetLine::balance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalEquity =
        equityLines.stream()
            .map(BalanceSheetLine::balance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new BalanceSheet(
        asOfDate,
        assetLines,
        liabilityLines,
        equityLines,
        totalAssets,
        totalLiabilities,
        totalEquity);
  }

  private List<BalanceSheetLine> calculateBalances(
      List<Account> accounts, LocalDate asOfDate, boolean debitPositive) {
    List<BalanceSheetLine> lines = new ArrayList<>();

    for (Account account : accounts) {
      BigDecimal balance = ledgerEntryRepository.getBalanceByAccountAsOf(account, asOfDate);
      if (balance == null) balance = BigDecimal.ZERO;

      // For liability and equity, credit is positive
      if (!debitPositive) {
        balance = balance.negate();
      }

      if (balance.compareTo(BigDecimal.ZERO) != 0) {
        lines.add(new BalanceSheetLine(account, balance));
      }
    }

    return lines;
  }

  /**
   * Generates an AR (Accounts Receivable) Aging Report. Categorizes outstanding invoices into aging
   * buckets: Current, 1-30, 31-60, 61-90, 90+ days.
   */
  public ArAgingReport generateArAging(Company company, LocalDate asOfDate) {
    List<SalesInvoice> outstandingInvoices =
        salesInvoiceRepository.findOutstandingByCompany(company);

    Map<Contact, List<ArAgingLine>> byCustomer = new LinkedHashMap<>();
    BigDecimal totalCurrent = BigDecimal.ZERO;
    BigDecimal total1to30 = BigDecimal.ZERO;
    BigDecimal total31to60 = BigDecimal.ZERO;
    BigDecimal total61to90 = BigDecimal.ZERO;
    BigDecimal total90Plus = BigDecimal.ZERO;

    for (SalesInvoice invoice : outstandingInvoices) {
      BigDecimal balance = invoice.getBalance();
      if (balance.compareTo(BigDecimal.ZERO) <= 0) continue;

      int daysOverdue = 0;
      if (invoice.getDueDate() != null && asOfDate.isAfter(invoice.getDueDate())) {
        daysOverdue = (int) ChronoUnit.DAYS.between(invoice.getDueDate(), asOfDate);
      }

      // Create aging line
      ArAgingLine line =
          new ArAgingLine(
              invoice,
              invoice.getContact(),
              invoice.getInvoiceNumber(),
              invoice.getIssueDate(),
              invoice.getDueDate(),
              invoice.getTotal(),
              balance,
              daysOverdue,
              getAgingBucket(daysOverdue));

      byCustomer.computeIfAbsent(invoice.getContact(), k -> new ArrayList<>()).add(line);

      // Add to bucket totals
      if (daysOverdue <= 0) {
        totalCurrent = totalCurrent.add(balance);
      } else if (daysOverdue <= 30) {
        total1to30 = total1to30.add(balance);
      } else if (daysOverdue <= 60) {
        total31to60 = total31to60.add(balance);
      } else if (daysOverdue <= 90) {
        total61to90 = total61to90.add(balance);
      } else {
        total90Plus = total90Plus.add(balance);
      }
    }

    // Flatten and create customer summaries
    List<ArAgingLine> allLines =
        byCustomer.values().stream()
            .flatMap(List::stream)
            .sorted(
                Comparator.comparing((ArAgingLine l) -> l.customer().getName())
                    .thenComparing(ArAgingLine::dueDate))
            .collect(Collectors.toList());

    List<ArAgingCustomerSummary> customerSummaries =
        byCustomer.entrySet().stream()
            .map(
                entry -> {
                  Contact customer = entry.getKey();
                  List<ArAgingLine> lines = entry.getValue();
                  BigDecimal custCurrent = BigDecimal.ZERO;
                  BigDecimal cust1to30 = BigDecimal.ZERO;
                  BigDecimal cust31to60 = BigDecimal.ZERO;
                  BigDecimal cust61to90 = BigDecimal.ZERO;
                  BigDecimal cust90Plus = BigDecimal.ZERO;

                  for (ArAgingLine line : lines) {
                    BigDecimal balance = line.balance();
                    if (line.daysOverdue() <= 0) custCurrent = custCurrent.add(balance);
                    else if (line.daysOverdue() <= 30) cust1to30 = cust1to30.add(balance);
                    else if (line.daysOverdue() <= 60) cust31to60 = cust31to60.add(balance);
                    else if (line.daysOverdue() <= 90) cust61to90 = cust61to90.add(balance);
                    else cust90Plus = cust90Plus.add(balance);
                  }

                  return new ArAgingCustomerSummary(
                      customer,
                      custCurrent,
                      cust1to30,
                      cust31to60,
                      cust61to90,
                      cust90Plus,
                      custCurrent.add(cust1to30).add(cust31to60).add(cust61to90).add(cust90Plus));
                })
            .sorted(Comparator.comparing(s -> s.customer().getName()))
            .collect(Collectors.toList());

    BigDecimal grandTotal =
        totalCurrent.add(total1to30).add(total31to60).add(total61to90).add(total90Plus);

    return new ArAgingReport(
        asOfDate,
        allLines,
        customerSummaries,
        totalCurrent,
        total1to30,
        total31to60,
        total61to90,
        total90Plus,
        grandTotal);
  }

  /**
   * Generates an AP (Accounts Payable) Aging Report. Categorizes outstanding bills into aging
   * buckets: Current, 1-30, 31-60, 61-90, 90+ days.
   */
  public ApAgingReport generateApAging(Company company, LocalDate asOfDate) {
    List<SupplierBill> outstandingBills = supplierBillRepository.findOutstandingByCompany(company);

    Map<Contact, List<ApAgingLine>> bySupplier = new LinkedHashMap<>();
    BigDecimal totalCurrent = BigDecimal.ZERO;
    BigDecimal total1to30 = BigDecimal.ZERO;
    BigDecimal total31to60 = BigDecimal.ZERO;
    BigDecimal total61to90 = BigDecimal.ZERO;
    BigDecimal total90Plus = BigDecimal.ZERO;

    for (SupplierBill bill : outstandingBills) {
      BigDecimal balance = bill.getBalance();
      if (balance.compareTo(BigDecimal.ZERO) <= 0) continue;

      int daysOverdue = 0;
      if (bill.getDueDate() != null && asOfDate.isAfter(bill.getDueDate())) {
        daysOverdue = (int) ChronoUnit.DAYS.between(bill.getDueDate(), asOfDate);
      }

      ApAgingLine line =
          new ApAgingLine(
              bill,
              bill.getContact(),
              bill.getBillNumber(),
              bill.getBillDate(),
              bill.getDueDate(),
              bill.getTotal(),
              balance,
              daysOverdue,
              getAgingBucket(daysOverdue));

      bySupplier.computeIfAbsent(bill.getContact(), k -> new ArrayList<>()).add(line);

      // Add to bucket totals
      if (daysOverdue <= 0) {
        totalCurrent = totalCurrent.add(balance);
      } else if (daysOverdue <= 30) {
        total1to30 = total1to30.add(balance);
      } else if (daysOverdue <= 60) {
        total31to60 = total31to60.add(balance);
      } else if (daysOverdue <= 90) {
        total61to90 = total61to90.add(balance);
      } else {
        total90Plus = total90Plus.add(balance);
      }
    }

    // Flatten and create supplier summaries
    List<ApAgingLine> allLines =
        bySupplier.values().stream()
            .flatMap(List::stream)
            .sorted(
                Comparator.comparing((ApAgingLine l) -> l.supplier().getName())
                    .thenComparing(ApAgingLine::dueDate))
            .collect(Collectors.toList());

    List<ApAgingSupplierSummary> supplierSummaries =
        bySupplier.entrySet().stream()
            .map(
                entry -> {
                  Contact supplier = entry.getKey();
                  List<ApAgingLine> lines = entry.getValue();
                  BigDecimal suppCurrent = BigDecimal.ZERO;
                  BigDecimal supp1to30 = BigDecimal.ZERO;
                  BigDecimal supp31to60 = BigDecimal.ZERO;
                  BigDecimal supp61to90 = BigDecimal.ZERO;
                  BigDecimal supp90Plus = BigDecimal.ZERO;

                  for (ApAgingLine line : lines) {
                    BigDecimal balance = line.balance();
                    if (line.daysOverdue() <= 0) suppCurrent = suppCurrent.add(balance);
                    else if (line.daysOverdue() <= 30) supp1to30 = supp1to30.add(balance);
                    else if (line.daysOverdue() <= 60) supp31to60 = supp31to60.add(balance);
                    else if (line.daysOverdue() <= 90) supp61to90 = supp61to90.add(balance);
                    else supp90Plus = supp90Plus.add(balance);
                  }

                  return new ApAgingSupplierSummary(
                      supplier,
                      suppCurrent,
                      supp1to30,
                      supp31to60,
                      supp61to90,
                      supp90Plus,
                      suppCurrent.add(supp1to30).add(supp31to60).add(supp61to90).add(supp90Plus));
                })
            .sorted(Comparator.comparing(s -> s.supplier().getName()))
            .collect(Collectors.toList());

    BigDecimal grandTotal =
        totalCurrent.add(total1to30).add(total31to60).add(total61to90).add(total90Plus);

    return new ApAgingReport(
        asOfDate,
        allLines,
        supplierSummaries,
        totalCurrent,
        total1to30,
        total31to60,
        total61to90,
        total90Plus,
        grandTotal);
  }

  private String getAgingBucket(int daysOverdue) {
    if (daysOverdue <= 0) return "Current";
    if (daysOverdue <= 30) return "1-30 Days";
    if (daysOverdue <= 60) return "31-60 Days";
    if (daysOverdue <= 90) return "61-90 Days";
    return "90+ Days";
  }

  // Record classes for report data
  public record TrialBalance(
      LocalDate startDate,
      LocalDate endDate,
      List<TrialBalanceLine> lines,
      BigDecimal totalDebits,
      BigDecimal totalCredits) {
    public boolean isBalanced() {
      return totalDebits.compareTo(totalCredits) == 0;
    }
  }

  public record TrialBalanceLine(Account account, BigDecimal debits, BigDecimal credits) {}

  public record ProfitAndLoss(
      LocalDate startDate,
      LocalDate endDate,
      Department department, // null means all departments
      List<ProfitAndLossLine> incomeLines,
      List<ProfitAndLossLine> expenseLines,
      BigDecimal totalIncome,
      BigDecimal totalExpenses,
      BigDecimal netProfit) {}

  public record ProfitAndLossLine(Account account, BigDecimal amount) {}

  public record BalanceSheet(
      LocalDate asOfDate,
      List<BalanceSheetLine> assets,
      List<BalanceSheetLine> liabilities,
      List<BalanceSheetLine> equity,
      BigDecimal totalAssets,
      BigDecimal totalLiabilities,
      BigDecimal totalEquity) {
    public boolean isBalanced() {
      return totalAssets.compareTo(totalLiabilities.add(totalEquity)) == 0;
    }
  }

  public record BalanceSheetLine(Account account, BigDecimal balance) {}

  public record BudgetVsActual(
      LocalDate startDate,
      LocalDate endDate,
      Budget budget,
      Department department, // null means all departments
      List<BudgetVsActualLine> lines,
      BigDecimal totalBudget,
      BigDecimal totalActual,
      BigDecimal totalVariance,
      BigDecimal totalVariancePercent) {}

  public record BudgetVsActualLine(
      Account account,
      BigDecimal budgetAmount,
      BigDecimal actualAmount,
      BigDecimal variance,
      BigDecimal variancePercent) {}

  // AR Aging Report records
  public record ArAgingReport(
      LocalDate asOfDate,
      List<ArAgingLine> lines,
      List<ArAgingCustomerSummary> customerSummaries,
      BigDecimal totalCurrent,
      BigDecimal total1to30,
      BigDecimal total31to60,
      BigDecimal total61to90,
      BigDecimal total90Plus,
      BigDecimal grandTotal) {}

  public record ArAgingLine(
      SalesInvoice invoice,
      Contact customer,
      String invoiceNumber,
      LocalDate invoiceDate,
      LocalDate dueDate,
      BigDecimal invoiceTotal,
      BigDecimal balance,
      int daysOverdue,
      String agingBucket) {}

  public record ArAgingCustomerSummary(
      Contact customer,
      BigDecimal current,
      BigDecimal days1to30,
      BigDecimal days31to60,
      BigDecimal days61to90,
      BigDecimal days90Plus,
      BigDecimal total) {}

  // AP Aging Report records
  public record ApAgingReport(
      LocalDate asOfDate,
      List<ApAgingLine> lines,
      List<ApAgingSupplierSummary> supplierSummaries,
      BigDecimal totalCurrent,
      BigDecimal total1to30,
      BigDecimal total31to60,
      BigDecimal total61to90,
      BigDecimal total90Plus,
      BigDecimal grandTotal) {}

  public record ApAgingLine(
      SupplierBill bill,
      Contact supplier,
      String billNumber,
      LocalDate billDate,
      LocalDate dueDate,
      BigDecimal billTotal,
      BigDecimal balance,
      int daysOverdue,
      String agingBucket) {}

  public record ApAgingSupplierSummary(
      Contact supplier,
      BigDecimal current,
      BigDecimal days1to30,
      BigDecimal days31to60,
      BigDecimal days61to90,
      BigDecimal days90Plus,
      BigDecimal total) {}

  // ==================== CASHFLOW REPORT ====================

  /**
   * Generates a Cashflow Statement for the given date range. Shows cash inflows and outflows
   * through bank accounts. This is a simplified cashflow report based on direct cash movements.
   */
  public CashflowStatement generateCashflow(
      Company company, LocalDate startDate, LocalDate endDate) {
    return generateCashflow(company, startDate, endDate, Integer.MAX_VALUE);
  }

  /**
   * Generates a Cashflow Statement with security level filtering.
   *
   * @param company the company
   * @param startDate start of reporting period
   * @param endDate end of reporting period
   * @param maxSecurityLevel the user's maximum security level
   * @return the cashflow statement with restricted accounts filtered out
   */
  public CashflowStatement generateCashflow(
      Company company, LocalDate startDate, LocalDate endDate, int maxSecurityLevel) {
    // Get all bank accounts, filtered by security level
    List<Account> bankAccounts =
        accountRepository.findBankAccountsByCompanyWithSecurityLevel(company, maxSecurityLevel);

    BigDecimal openingBalance = BigDecimal.ZERO;
    BigDecimal closingBalance = BigDecimal.ZERO;
    BigDecimal totalInflows = BigDecimal.ZERO;
    BigDecimal totalOutflows = BigDecimal.ZERO;

    List<CashflowAccountSummary> accountSummaries = new ArrayList<>();
    List<CashflowLine> allLines = new ArrayList<>();

    LocalDate dayBeforeStart = startDate.minusDays(1);

    for (Account bankAccount : bankAccounts) {
      // Calculate opening balance (as of day before start date)
      BigDecimal accountOpeningBalance =
          ledgerEntryRepository.getBalanceByAccountAsOf(bankAccount, dayBeforeStart);
      if (accountOpeningBalance == null) accountOpeningBalance = BigDecimal.ZERO;

      // Calculate closing balance (as of end date)
      BigDecimal accountClosingBalance =
          ledgerEntryRepository.getBalanceByAccountAsOf(bankAccount, endDate);
      if (accountClosingBalance == null) accountClosingBalance = BigDecimal.ZERO;

      // Get all entries for this bank account in the date range
      List<LedgerEntry> entries =
          ledgerEntryRepository.findByAccountAndDateRange(bankAccount, startDate, endDate);

      BigDecimal accountInflows = BigDecimal.ZERO;
      BigDecimal accountOutflows = BigDecimal.ZERO;

      for (LedgerEntry entry : entries) {
        // For bank accounts (assets), debits increase balance (inflows), credits decrease
        // (outflows)
        BigDecimal debit = entry.getAmountDr() != null ? entry.getAmountDr() : BigDecimal.ZERO;
        BigDecimal credit = entry.getAmountCr() != null ? entry.getAmountCr() : BigDecimal.ZERO;

        if (debit.compareTo(BigDecimal.ZERO) > 0) {
          accountInflows = accountInflows.add(debit);
          allLines.add(
              new CashflowLine(
                  entry.getEntryDate(),
                  bankAccount,
                  entry.getTransaction() != null
                      ? entry.getTransaction().getDescription()
                      : "Entry",
                  entry.getTransaction() != null ? entry.getTransaction().getReference() : null,
                  debit,
                  CashflowDirection.INFLOW));
        }
        if (credit.compareTo(BigDecimal.ZERO) > 0) {
          accountOutflows = accountOutflows.add(credit);
          allLines.add(
              new CashflowLine(
                  entry.getEntryDate(),
                  bankAccount,
                  entry.getTransaction() != null
                      ? entry.getTransaction().getDescription()
                      : "Entry",
                  entry.getTransaction() != null ? entry.getTransaction().getReference() : null,
                  credit,
                  CashflowDirection.OUTFLOW));
        }
      }

      openingBalance = openingBalance.add(accountOpeningBalance);
      closingBalance = closingBalance.add(accountClosingBalance);
      totalInflows = totalInflows.add(accountInflows);
      totalOutflows = totalOutflows.add(accountOutflows);

      // Only add accounts with activity or balances
      if (accountInflows.compareTo(BigDecimal.ZERO) != 0
          || accountOutflows.compareTo(BigDecimal.ZERO) != 0
          || accountOpeningBalance.compareTo(BigDecimal.ZERO) != 0) {
        accountSummaries.add(
            new CashflowAccountSummary(
                bankAccount,
                accountOpeningBalance,
                accountInflows,
                accountOutflows,
                accountClosingBalance));
      }
    }

    BigDecimal netCashFlow = totalInflows.subtract(totalOutflows);

    // Sort lines by date
    allLines.sort(
        Comparator.comparing(CashflowLine::date).thenComparing(l -> l.account().getCode()));

    return new CashflowStatement(
        startDate,
        endDate,
        openingBalance,
        closingBalance,
        totalInflows,
        totalOutflows,
        netCashFlow,
        accountSummaries,
        allLines);
  }

  // Cashflow Report records
  public record CashflowStatement(
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal openingBalance,
      BigDecimal closingBalance,
      BigDecimal totalInflows,
      BigDecimal totalOutflows,
      BigDecimal netCashFlow,
      List<CashflowAccountSummary> accountSummaries,
      List<CashflowLine> lines) {
    public boolean isReconciled() {
      // Opening balance + net cash flow should equal closing balance
      return openingBalance.add(netCashFlow).compareTo(closingBalance) == 0;
    }
  }

  public record CashflowAccountSummary(
      Account account,
      BigDecimal openingBalance,
      BigDecimal inflows,
      BigDecimal outflows,
      BigDecimal closingBalance) {
    public BigDecimal netChange() {
      return inflows.subtract(outflows);
    }
  }

  public record CashflowLine(
      LocalDate date,
      Account account,
      String description,
      String reference,
      BigDecimal amount,
      CashflowDirection direction) {}

  public enum CashflowDirection {
    INFLOW,
    OUTFLOW
  }

  // ==================== BANK REGISTER REPORT ====================

  /**
   * Generates a Bank Register report for a specific bank account. Shows a chronological list of all
   * transactions affecting the bank account with running balance.
   *
   * <p>This report is essential for reconciling bank statements and auditing cash movements through
   * a specific bank account.
   */
  public BankRegister generateBankRegister(
      Company company, Account bankAccount, LocalDate startDate, LocalDate endDate) {
    return generateBankRegister(company, bankAccount, startDate, endDate, Integer.MAX_VALUE);
  }

  /**
   * Generates a Bank Register report with security level filtering.
   *
   * @param company the company
   * @param bankAccount the bank account to report on
   * @param startDate start of reporting period
   * @param endDate end of reporting period
   * @param maxSecurityLevel the user's maximum security level
   * @return the bank register with transaction details and running balance
   */
  public BankRegister generateBankRegister(
      Company company,
      Account bankAccount,
      LocalDate startDate,
      LocalDate endDate,
      int maxSecurityLevel) {
    // Verify the user has access to view this bank account
    if (bankAccount.getSecurityLevel() != null
        && bankAccount.getSecurityLevel() > maxSecurityLevel) {
      throw new IllegalArgumentException(
          "Access denied: insufficient security level to view this bank account");
    }

    // Calculate opening balance (as of day before start date)
    LocalDate dayBeforeStart = startDate.minusDays(1);
    BigDecimal openingBalance =
        ledgerEntryRepository.getBalanceByAccountAsOf(bankAccount, dayBeforeStart);
    if (openingBalance == null) openingBalance = BigDecimal.ZERO;

    // Get all ledger entries for this bank account in the date range
    List<LedgerEntry> entries =
        ledgerEntryRepository.findByAccountAndDateRange(bankAccount, startDate, endDate);

    // Build register lines with running balance
    List<BankRegisterLine> lines = new ArrayList<>();
    BigDecimal runningBalance = openingBalance;

    for (LedgerEntry entry : entries) {
      BigDecimal debit = entry.getAmountDr() != null ? entry.getAmountDr() : BigDecimal.ZERO;
      BigDecimal credit = entry.getAmountCr() != null ? entry.getAmountCr() : BigDecimal.ZERO;

      // For bank accounts (assets), debits increase balance, credits decrease
      runningBalance = runningBalance.add(debit).subtract(credit);

      String reference =
          entry.getTransaction() != null ? entry.getTransaction().getReference() : "";
      String description =
          entry.getTransaction() != null ? entry.getTransaction().getDescription() : "";
      String transactionType =
          entry.getTransaction() != null ? entry.getTransaction().getType().name() : "";

      lines.add(
          new BankRegisterLine(
              entry.getEntryDate(),
              reference,
              description,
              transactionType,
              debit.compareTo(BigDecimal.ZERO) > 0 ? debit : null,
              credit.compareTo(BigDecimal.ZERO) > 0 ? credit : null,
              runningBalance,
              entry.getTransaction() != null ? entry.getTransaction().getId() : null));
    }

    // Calculate closing balance
    BigDecimal closingBalance = ledgerEntryRepository.getBalanceByAccountAsOf(bankAccount, endDate);
    if (closingBalance == null) closingBalance = BigDecimal.ZERO;

    // Calculate totals
    BigDecimal totalDebits =
        entries.stream()
            .map(e -> e.getAmountDr() != null ? e.getAmountDr() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalCredits =
        entries.stream()
            .map(e -> e.getAmountCr() != null ? e.getAmountCr() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new BankRegister(
        bankAccount,
        startDate,
        endDate,
        openingBalance,
        closingBalance,
        totalDebits,
        totalCredits,
        lines);
  }

  // Bank Register Report records
  public record BankRegister(
      Account bankAccount,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal openingBalance,
      BigDecimal closingBalance,
      BigDecimal totalDebits,
      BigDecimal totalCredits,
      List<BankRegisterLine> lines) {
    /**
     * Verifies that opening + debits - credits = closing. This should always be true for a
     * correctly generated report.
     */
    public boolean isReconciled() {
      BigDecimal expected = openingBalance.add(totalDebits).subtract(totalCredits);
      return expected.compareTo(closingBalance) == 0;
    }

    /** Returns the net change in the account during the period. */
    public BigDecimal netChange() {
      return totalDebits.subtract(totalCredits);
    }
  }

  public record BankRegisterLine(
      LocalDate date,
      String reference,
      String description,
      String transactionType,
      BigDecimal debit,
      BigDecimal credit,
      BigDecimal runningBalance,
      Long transactionId) {}
}
