package com.example.application.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Company;
import com.example.application.domain.PayableAllocation;
import com.example.application.domain.SupplierBill;
import com.example.application.domain.Transaction;

@Repository
public interface PayableAllocationRepository extends JpaRepository<PayableAllocation, Long> {

  List<PayableAllocation> findBySupplierBill(SupplierBill supplierBill);

  @Query(
      "SELECT pa FROM PayableAllocation pa "
          + "JOIN FETCH pa.supplierBill sb "
          + "LEFT JOIN FETCH sb.contact "
          + "WHERE pa.paymentTransaction = :paymentTransaction")
  List<PayableAllocation> findByPaymentTransaction(
      @Param("paymentTransaction") Transaction paymentTransaction);

  List<PayableAllocation> findByCompanyOrderByAllocatedAtDesc(Company company);

  // Total allocated to a specific bill
  @Query("SELECT COALESCE(SUM(a.amount), 0) FROM PayableAllocation a WHERE a.supplierBill = :bill")
  BigDecimal sumByBill(@Param("bill") SupplierBill bill);

  // Total allocated from a specific payment
  @Query(
      "SELECT COALESCE(SUM(a.amount), 0) FROM PayableAllocation a WHERE a.paymentTransaction = :payment")
  BigDecimal sumByPayment(@Param("payment") Transaction payment);

  // Check if allocation exists
  boolean existsByPaymentTransactionAndSupplierBill(
      Transaction paymentTransaction, SupplierBill supplierBill);
}
