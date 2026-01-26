package com.example.application.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Company;
import com.example.application.domain.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

  @Query(
      "SELECT DISTINCT t FROM Transaction t "
          + "LEFT JOIN FETCH t.lines l "
          + "LEFT JOIN FETCH l.account "
          + "WHERE t.company = :company "
          + "ORDER BY t.transactionDate DESC")
  List<Transaction> findByCompanyOrderByTransactionDateDesc(@Param("company") Company company);

  @Query(
      "SELECT DISTINCT t FROM Transaction t "
          + "LEFT JOIN FETCH t.lines l "
          + "LEFT JOIN FETCH l.account "
          + "WHERE t.company = :company AND t.status = :status "
          + "ORDER BY t.transactionDate DESC")
  List<Transaction> findByCompanyAndStatusOrderByTransactionDateDesc(
      @Param("company") Company company, @Param("status") Transaction.Status status);

  @Query(
      "SELECT DISTINCT t FROM Transaction t "
          + "LEFT JOIN FETCH t.lines l "
          + "LEFT JOIN FETCH l.account "
          + "WHERE t.company = :company "
          + "AND t.transactionDate BETWEEN :startDate AND :endDate "
          + "ORDER BY t.transactionDate DESC")
  List<Transaction> findByCompanyAndDateRange(
      @Param("company") Company company,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  @Query(
      "SELECT DISTINCT t FROM Transaction t "
          + "LEFT JOIN FETCH t.lines l "
          + "LEFT JOIN FETCH l.account "
          + "WHERE t.company = :company "
          + "AND t.status = :status "
          + "AND t.transactionDate BETWEEN :startDate AND :endDate "
          + "ORDER BY t.transactionDate DESC")
  List<Transaction> findByCompanyAndStatusAndDateRange(
      @Param("company") Company company,
      @Param("status") Transaction.Status status,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  List<Transaction> findByCompanyAndType(Company company, Transaction.TransactionType type);
}
