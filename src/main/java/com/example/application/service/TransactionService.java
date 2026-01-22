package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Transaction createTransaction(Company company, Transaction.TransactionType type,
                                          LocalDate date, String description, User createdBy) {
        Transaction transaction = new Transaction(company, type, date);
        transaction.setDescription(description);
        transaction.setCreatedBy(createdBy);
        return transactionRepository.save(transaction);
    }

    public Transaction addLine(Transaction transaction, Account account,
                                BigDecimal amount, TransactionLine.Direction direction) {
        if (transaction.isPosted()) {
            throw new IllegalStateException("Cannot modify a posted transaction");
        }

        TransactionLine line = new TransactionLine(account, amount, direction);
        transaction.addLine(line);
        return transactionRepository.save(transaction);
    }

    public Transaction addLine(Transaction transaction, Account account,
                                BigDecimal amount, TransactionLine.Direction direction,
                                String taxCode, String memo) {
        if (transaction.isPosted()) {
            throw new IllegalStateException("Cannot modify a posted transaction");
        }

        TransactionLine line = new TransactionLine(account, amount, direction);
        line.setTaxCode(taxCode);
        line.setMemo(memo);
        transaction.addLine(line);
        return transactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public Optional<Transaction> findById(Long id) {
        return transactionRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Transaction> findByCompany(Company company) {
        return transactionRepository.findByCompanyOrderByTransactionDateDesc(company);
    }

    @Transactional(readOnly = true)
    public List<Transaction> findDraftsByCompany(Company company) {
        return transactionRepository.findByCompanyAndStatusOrderByTransactionDateDesc(
            company, Transaction.Status.DRAFT);
    }

    @Transactional(readOnly = true)
    public List<Transaction> findPostedByCompany(Company company) {
        return transactionRepository.findByCompanyAndStatusOrderByTransactionDateDesc(
            company, Transaction.Status.POSTED);
    }

    @Transactional(readOnly = true)
    public List<Transaction> findByCompanyAndDateRange(Company company,
                                                        LocalDate startDate,
                                                        LocalDate endDate) {
        return transactionRepository.findByCompanyAndDateRange(company, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<Transaction> findPostedByCompanyAndDateRange(Company company,
                                                              LocalDate startDate,
                                                              LocalDate endDate) {
        return transactionRepository.findByCompanyAndStatusAndDateRange(
            company, Transaction.Status.POSTED, startDate, endDate);
    }

    public Transaction save(Transaction transaction) {
        return transactionRepository.save(transaction);
    }

    public void delete(Transaction transaction) {
        if (transaction.isPosted()) {
            throw new IllegalStateException("Cannot delete a posted transaction");
        }
        transactionRepository.delete(transaction);
    }

    /**
     * Calculates the balance of the transaction (debits - credits).
     * A balanced transaction should return zero.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateBalance(Transaction transaction) {
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;

        for (TransactionLine line : transaction.getLines()) {
            if (line.isDebit()) {
                debits = debits.add(line.getAmount());
            } else {
                credits = credits.add(line.getAmount());
            }
        }

        return debits.subtract(credits);
    }

    /**
     * Checks if the transaction is balanced (debits equal credits).
     */
    @Transactional(readOnly = true)
    public boolean isBalanced(Transaction transaction) {
        return calculateBalance(transaction).compareTo(BigDecimal.ZERO) == 0;
    }
}
