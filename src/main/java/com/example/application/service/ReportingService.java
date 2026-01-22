package com.example.application.service;

import com.example.application.domain.Account;
import com.example.application.domain.Company;
import com.example.application.domain.LedgerEntry;
import com.example.application.repository.AccountRepository;
import com.example.application.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Service for generating financial reports.
 * Produces Trial Balance, P&L, and Balance Sheet from ledger entries.
 */
@Service
@Transactional(readOnly = true)
public class ReportingService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public ReportingService(AccountRepository accountRepository,
                            LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Generates a Trial Balance for the given company and date range.
     * Returns a map of account to balance (debits positive, credits negative).
     */
    public TrialBalance generateTrialBalance(Company company, LocalDate startDate, LocalDate endDate) {
        List<Account> accounts = accountRepository.findByCompanyOrderByCode(company);
        List<TrialBalanceLine> lines = new ArrayList<>();

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (Account account : accounts) {
            BigDecimal debits = ledgerEntryRepository.sumDebitsByAccountAsOf(account, endDate);
            BigDecimal credits = ledgerEntryRepository.sumCreditsByAccountAsOf(account, endDate);

            if (debits == null) debits = BigDecimal.ZERO;
            if (credits == null) credits = BigDecimal.ZERO;

            // Skip accounts with no activity
            if (debits.compareTo(BigDecimal.ZERO) == 0 &&
                credits.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            lines.add(new TrialBalanceLine(account, debits, credits));
            totalDebits = totalDebits.add(debits);
            totalCredits = totalCredits.add(credits);
        }

        return new TrialBalance(startDate, endDate, lines, totalDebits, totalCredits);
    }

    /**
     * Generates a Profit & Loss statement for the given date range.
     */
    public ProfitAndLoss generateProfitAndLoss(Company company, LocalDate startDate, LocalDate endDate) {
        List<Account> incomeAccounts = accountRepository.findByCompanyIdAndType(
            company.getId(), Account.AccountType.INCOME);
        List<Account> expenseAccounts = accountRepository.findByCompanyIdAndType(
            company.getId(), Account.AccountType.EXPENSE);

        List<ProfitAndLossLine> incomeLines = new ArrayList<>();
        List<ProfitAndLossLine> expenseLines = new ArrayList<>();

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;

        // Calculate income (credits - debits)
        for (Account account : incomeAccounts) {
            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountAndDateRange(
                account, startDate, endDate);

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
            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountAndDateRange(
                account, startDate, endDate);

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

        return new ProfitAndLoss(startDate, endDate, incomeLines, expenseLines,
            totalIncome, totalExpenses, netProfit);
    }

    /**
     * Generates a Balance Sheet as of the given date.
     */
    public BalanceSheet generateBalanceSheet(Company company, LocalDate asOfDate) {
        List<Account> assetAccounts = accountRepository.findByCompanyIdAndType(
            company.getId(), Account.AccountType.ASSET);
        List<Account> liabilityAccounts = accountRepository.findByCompanyIdAndType(
            company.getId(), Account.AccountType.LIABILITY);
        List<Account> equityAccounts = accountRepository.findByCompanyIdAndType(
            company.getId(), Account.AccountType.EQUITY);

        List<BalanceSheetLine> assetLines = calculateBalances(assetAccounts, asOfDate, true);
        List<BalanceSheetLine> liabilityLines = calculateBalances(liabilityAccounts, asOfDate, false);
        List<BalanceSheetLine> equityLines = calculateBalances(equityAccounts, asOfDate, false);

        BigDecimal totalAssets = assetLines.stream()
            .map(BalanceSheetLine::balance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalLiabilities = liabilityLines.stream()
            .map(BalanceSheetLine::balance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEquity = equityLines.stream()
            .map(BalanceSheetLine::balance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BalanceSheet(asOfDate, assetLines, liabilityLines, equityLines,
            totalAssets, totalLiabilities, totalEquity);
    }

    private List<BalanceSheetLine> calculateBalances(List<Account> accounts,
                                                      LocalDate asOfDate,
                                                      boolean debitPositive) {
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

    // Record classes for report data
    public record TrialBalance(
        LocalDate startDate,
        LocalDate endDate,
        List<TrialBalanceLine> lines,
        BigDecimal totalDebits,
        BigDecimal totalCredits
    ) {
        public boolean isBalanced() {
            return totalDebits.compareTo(totalCredits) == 0;
        }
    }

    public record TrialBalanceLine(Account account, BigDecimal debits, BigDecimal credits) {}

    public record ProfitAndLoss(
        LocalDate startDate,
        LocalDate endDate,
        List<ProfitAndLossLine> incomeLines,
        List<ProfitAndLossLine> expenseLines,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal netProfit
    ) {}

    public record ProfitAndLossLine(Account account, BigDecimal amount) {}

    public record BalanceSheet(
        LocalDate asOfDate,
        List<BalanceSheetLine> assets,
        List<BalanceSheetLine> liabilities,
        List<BalanceSheetLine> equity,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal totalEquity
    ) {
        public boolean isBalanced() {
            return totalAssets.compareTo(totalLiabilities.add(totalEquity)) == 0;
        }
    }

    public record BalanceSheetLine(Account account, BigDecimal balance) {}
}
