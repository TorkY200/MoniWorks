package com.example.application.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Company;
import com.example.application.domain.Contact;
import com.example.application.domain.ReceivableAllocation;
import com.example.application.domain.SalesInvoice;
import com.example.application.domain.Transaction;

@Repository
public interface ReceivableAllocationRepository extends JpaRepository<ReceivableAllocation, Long> {

  List<ReceivableAllocation> findBySalesInvoice(SalesInvoice salesInvoice);

  @Query(
      "SELECT ra FROM ReceivableAllocation ra "
          + "JOIN FETCH ra.salesInvoice si "
          + "LEFT JOIN FETCH si.contact "
          + "WHERE ra.receiptTransaction = :receiptTransaction")
  List<ReceivableAllocation> findByReceiptTransaction(
      @Param("receiptTransaction") Transaction receiptTransaction);

  List<ReceivableAllocation> findByCompanyOrderByAllocatedAtDesc(Company company);

  // Total allocated to a specific invoice
  @Query(
      "SELECT COALESCE(SUM(a.amount), 0) FROM ReceivableAllocation a WHERE a.salesInvoice = :invoice")
  BigDecimal sumByInvoice(@Param("invoice") SalesInvoice invoice);

  // Total allocated from a specific receipt
  @Query(
      "SELECT COALESCE(SUM(a.amount), 0) FROM ReceivableAllocation a WHERE a.receiptTransaction = :receipt")
  BigDecimal sumByReceipt(@Param("receipt") Transaction receipt);

  // Check if allocation exists
  boolean existsByReceiptTransactionAndSalesInvoice(
      Transaction receiptTransaction, SalesInvoice salesInvoice);

  // Find allocations for a contact's invoices within a date range (for balance-forward statements)
  @Query(
      "SELECT a FROM ReceivableAllocation a WHERE a.company = :company "
          + "AND a.salesInvoice.contact = :contact "
          + "AND a.allocatedAt >= :startDate AND a.allocatedAt < :endDate "
          + "ORDER BY a.allocatedAt ASC")
  List<ReceivableAllocation> findByContactAndDateRange(
      @Param("company") Company company,
      @Param("contact") Contact contact,
      @Param("startDate") java.time.Instant startDate,
      @Param("endDate") java.time.Instant endDate);

  /**
   * Find all allocations for a company within a date range (for cash basis GST returns). Uses
   * allocatedAt timestamp to determine when payment was received.
   */
  @Query(
      "SELECT a FROM ReceivableAllocation a "
          + "JOIN FETCH a.salesInvoice si "
          + "WHERE a.company = :company "
          + "AND a.allocatedAt >= :startDate AND a.allocatedAt < :endDate "
          + "ORDER BY a.allocatedAt ASC")
  List<ReceivableAllocation> findByCompanyAndAllocatedAtRange(
      @Param("company") Company company,
      @Param("startDate") java.time.Instant startDate,
      @Param("endDate") java.time.Instant endDate);
}
