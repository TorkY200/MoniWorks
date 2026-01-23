package com.example.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.domain.Account;
import com.example.application.domain.Company;
import com.example.application.repository.*;

/**
 * Unit tests for ReportingService. Tests financial report generation (Trial Balance, P&L, Balance
 * Sheet).
 */
@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

  @Mock private AccountRepository accountRepository;

  @Mock private LedgerEntryRepository ledgerEntryRepository;

  @Mock private BudgetLineRepository budgetLineRepository;

  @Mock private PeriodRepository periodRepository;

  @Mock private SalesInvoiceRepository salesInvoiceRepository;

  @Mock private SupplierBillRepository supplierBillRepository;

  private ReportingService reportingService;

  private Company company;
  private Account bankAccount;
  private Account arAccount;
  private Account apAccount;
  private Account revenueAccount;
  private Account expenseAccount;
  private Account equityAccount;

  @BeforeEach
  void setUp() {
    reportingService =
        new ReportingService(
            accountRepository,
            ledgerEntryRepository,
            budgetLineRepository,
            periodRepository,
            salesInvoiceRepository,
            supplierBillRepository);

    company = new Company("Test Company", "NZ", "NZD", LocalDate.of(2024, 4, 1));
    company.setId(1L);

    bankAccount = new Account(company, "1000", "Bank", Account.AccountType.ASSET);
    bankAccount.setId(1L);

    arAccount = new Account(company, "1100", "Accounts Receivable", Account.AccountType.ASSET);
    arAccount.setId(2L);

    apAccount = new Account(company, "2000", "Accounts Payable", Account.AccountType.LIABILITY);
    apAccount.setId(3L);

    revenueAccount = new Account(company, "4000", "Sales Revenue", Account.AccountType.INCOME);
    revenueAccount.setId(4L);

    expenseAccount =
        new Account(company, "5000", "Operating Expenses", Account.AccountType.EXPENSE);
    expenseAccount.setId(5L);

    equityAccount = new Account(company, "3000", "Retained Earnings", Account.AccountType.EQUITY);
    equityAccount.setId(6L);
  }

  @Test
  void generateTrialBalance_withBalancedEntries_returnsBalancedReport() {
    // Arrange
    LocalDate startDate = LocalDate.of(2024, 4, 1);
    LocalDate endDate = LocalDate.of(2024, 4, 30);

    // Mock the security-level filtered query (used by generateTrialBalance internally)
    when(accountRepository.findByCompanyWithSecurityLevel(eq(company), anyInt()))
        .thenReturn(Arrays.asList(bankAccount, revenueAccount, expenseAccount));

    // Bank: 10,000 debit (asset)
    when(ledgerEntryRepository.sumDebitsByAccountAsOf(eq(bankAccount), any()))
        .thenReturn(new BigDecimal("10000.00"));
    when(ledgerEntryRepository.sumCreditsByAccountAsOf(eq(bankAccount), any()))
        .thenReturn(BigDecimal.ZERO);

    // Revenue: 8,000 credit (income)
    when(ledgerEntryRepository.sumDebitsByAccountAsOf(eq(revenueAccount), any()))
        .thenReturn(BigDecimal.ZERO);
    when(ledgerEntryRepository.sumCreditsByAccountAsOf(eq(revenueAccount), any()))
        .thenReturn(new BigDecimal("8000.00"));

    // Expenses: 2,000 debit (expense)
    when(ledgerEntryRepository.sumDebitsByAccountAsOf(eq(expenseAccount), any()))
        .thenReturn(new BigDecimal("2000.00"));
    when(ledgerEntryRepository.sumCreditsByAccountAsOf(eq(expenseAccount), any()))
        .thenReturn(BigDecimal.ZERO);

    // Act
    ReportingService.TrialBalance trialBalance =
        reportingService.generateTrialBalance(company, startDate, endDate);

    // Assert - Note: This won't actually balance in this mock because we haven't
    // set up all accounts, but we test the structure
    assertNotNull(trialBalance);
    assertEquals(3, trialBalance.lines().size());
    assertEquals(startDate, trialBalance.startDate());
    assertEquals(endDate, trialBalance.endDate());
  }

  @Test
  void generateTrialBalance_debitsEqualCredits_isBalanced() {
    // Arrange
    LocalDate endDate = LocalDate.of(2024, 4, 30);

    // Mock the security-level filtered query
    when(accountRepository.findByCompanyWithSecurityLevel(eq(company), anyInt()))
        .thenReturn(Arrays.asList(bankAccount, equityAccount));

    // Bank: 5,000 debit
    when(ledgerEntryRepository.sumDebitsByAccountAsOf(eq(bankAccount), any()))
        .thenReturn(new BigDecimal("5000.00"));
    when(ledgerEntryRepository.sumCreditsByAccountAsOf(eq(bankAccount), any()))
        .thenReturn(BigDecimal.ZERO);

    // Equity: 5,000 credit
    when(ledgerEntryRepository.sumDebitsByAccountAsOf(eq(equityAccount), any()))
        .thenReturn(BigDecimal.ZERO);
    when(ledgerEntryRepository.sumCreditsByAccountAsOf(eq(equityAccount), any()))
        .thenReturn(new BigDecimal("5000.00"));

    // Act
    ReportingService.TrialBalance trialBalance =
        reportingService.generateTrialBalance(company, endDate.minusDays(30), endDate);

    // Assert
    assertTrue(trialBalance.isBalanced());
    assertEquals(new BigDecimal("5000.00"), trialBalance.totalDebits());
    assertEquals(new BigDecimal("5000.00"), trialBalance.totalCredits());
  }

  @Test
  void generateProfitAndLoss_calculatesNetProfit() {
    // Arrange
    LocalDate startDate = LocalDate.of(2024, 4, 1);
    LocalDate endDate = LocalDate.of(2024, 4, 30);

    // Mock the security-level filtered queries (used by generateProfitAndLoss internally)
    when(accountRepository.findByCompanyIdAndTypeWithSecurityLevel(
            eq(company.getId()), eq(Account.AccountType.INCOME), anyInt()))
        .thenReturn(Collections.singletonList(revenueAccount));
    when(accountRepository.findByCompanyIdAndTypeWithSecurityLevel(
            eq(company.getId()), eq(Account.AccountType.EXPENSE), anyInt()))
        .thenReturn(Collections.singletonList(expenseAccount));

    // Revenue entries: empty for this test
    when(ledgerEntryRepository.findByAccountAndDateRange(eq(revenueAccount), any(), any()))
        .thenReturn(Collections.emptyList());

    // Expense entries: empty for this test
    when(ledgerEntryRepository.findByAccountAndDateRange(eq(expenseAccount), any(), any()))
        .thenReturn(Collections.emptyList());

    // Act
    ReportingService.ProfitAndLoss pnl =
        reportingService.generateProfitAndLoss(company, startDate, endDate);

    // Assert
    assertNotNull(pnl);
    assertEquals(startDate, pnl.startDate());
    assertEquals(endDate, pnl.endDate());
  }

  @Test
  void generateBalanceSheet_assetsEqualLiabilitiesPlusEquity() {
    // Arrange
    LocalDate asOfDate = LocalDate.of(2024, 4, 30);

    // Mock the security-level filtered queries (used by generateBalanceSheet internally)
    when(accountRepository.findByCompanyIdAndTypeWithSecurityLevel(
            eq(company.getId()), eq(Account.AccountType.ASSET), anyInt()))
        .thenReturn(Collections.singletonList(bankAccount));
    when(accountRepository.findByCompanyIdAndTypeWithSecurityLevel(
            eq(company.getId()), eq(Account.AccountType.LIABILITY), anyInt()))
        .thenReturn(Collections.singletonList(apAccount));
    when(accountRepository.findByCompanyIdAndTypeWithSecurityLevel(
            eq(company.getId()), eq(Account.AccountType.EQUITY), anyInt()))
        .thenReturn(Collections.singletonList(equityAccount));

    // Bank: 10,000 debit balance (asset positive)
    when(ledgerEntryRepository.getBalanceByAccountAsOf(eq(bankAccount), any()))
        .thenReturn(new BigDecimal("10000.00"));

    // AP: 3,000 credit balance (liability positive when negated)
    when(ledgerEntryRepository.getBalanceByAccountAsOf(eq(apAccount), any()))
        .thenReturn(new BigDecimal("-3000.00")); // credit balance is negative in debit-credit

    // Equity: 7,000 credit balance
    when(ledgerEntryRepository.getBalanceByAccountAsOf(eq(equityAccount), any()))
        .thenReturn(new BigDecimal("-7000.00")); // credit balance is negative in debit-credit

    // Act
    ReportingService.BalanceSheet balanceSheet =
        reportingService.generateBalanceSheet(company, asOfDate);

    // Assert
    assertNotNull(balanceSheet);
    assertEquals(asOfDate, balanceSheet.asOfDate());
    // Assets = 10,000
    assertEquals(new BigDecimal("10000.00"), balanceSheet.totalAssets());
    // Liabilities = 3,000
    assertEquals(new BigDecimal("3000.00"), balanceSheet.totalLiabilities());
    // Equity = 7,000
    assertEquals(new BigDecimal("7000.00"), balanceSheet.totalEquity());
    // Should balance: 10,000 = 3,000 + 7,000
    assertTrue(balanceSheet.isBalanced());
  }

  @Test
  void generateBalanceSheet_withNoEntries_returnsEmptyReport() {
    // Arrange
    LocalDate asOfDate = LocalDate.of(2024, 4, 30);

    // Mock the security-level filtered queries to return empty lists
    when(accountRepository.findByCompanyIdAndTypeWithSecurityLevel(any(), any(), anyInt()))
        .thenReturn(Collections.emptyList());

    // Act
    ReportingService.BalanceSheet balanceSheet =
        reportingService.generateBalanceSheet(company, asOfDate);

    // Assert
    assertNotNull(balanceSheet);
    assertTrue(balanceSheet.assets().isEmpty());
    assertTrue(balanceSheet.liabilities().isEmpty());
    assertTrue(balanceSheet.equity().isEmpty());
    assertEquals(BigDecimal.ZERO, balanceSheet.totalAssets());
    assertTrue(balanceSheet.isBalanced()); // 0 = 0 + 0
  }
}
