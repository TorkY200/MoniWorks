package com.example.application.service;

import com.example.application.domain.Account;
import com.example.application.domain.Company;
import com.example.application.repository.AccountRepository;
import com.example.application.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AccountService(AccountRepository accountRepository,
                          LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public Account createAccount(Company company, String code, String name,
                                  Account.AccountType type) {
        if (accountRepository.existsByCompanyAndCode(company, code)) {
            throw new IllegalArgumentException("Account code already exists: " + code);
        }
        Account account = new Account(company, code, name, type);
        return accountRepository.save(account);
    }

    public Account createAccount(Company company, String code, String name,
                                  Account.AccountType type, Account parent) {
        Account account = createAccount(company, code, name, type);
        account.setParent(parent);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<Account> findByCompany(Company company) {
        return accountRepository.findByCompanyOrderByCode(company);
    }

    @Transactional(readOnly = true)
    public List<Account> findActiveByCompany(Company company) {
        return accountRepository.findByCompanyAndActiveOrderByCode(company, true);
    }

    @Transactional(readOnly = true)
    public Optional<Account> findByCompanyAndCode(Company company, String code) {
        return accountRepository.findByCompanyAndCode(company, code);
    }

    @Transactional(readOnly = true)
    public Optional<Account> findById(Long id) {
        return accountRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Account> findRootAccounts(Company company) {
        return accountRepository.findRootAccountsByCompany(company);
    }

    @Transactional(readOnly = true)
    public List<Account> findChildren(Account parent) {
        return accountRepository.findByParent(parent);
    }

    @Transactional(readOnly = true)
    public List<Account> findByType(Long companyId, Account.AccountType type) {
        return accountRepository.findByCompanyIdAndType(companyId, type);
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Account account, LocalDate asOfDate) {
        BigDecimal balance = ledgerEntryRepository.getBalanceByAccountAsOf(account, asOfDate);

        // For liability, equity, and income accounts, credit increases the balance
        // So we negate the debit-credit difference for proper display
        if (account.getType() == Account.AccountType.LIABILITY ||
            account.getType() == Account.AccountType.EQUITY ||
            account.getType() == Account.AccountType.INCOME) {
            balance = balance.negate();
        }

        return balance;
    }

    public Account save(Account account) {
        return accountRepository.save(account);
    }

    public void deactivate(Account account) {
        account.setActive(false);
        accountRepository.save(account);
    }
}
