package com.example.application.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.SupplierBill;
import com.example.application.domain.SupplierBillLine;

@Repository
public interface SupplierBillLineRepository extends JpaRepository<SupplierBillLine, Long> {

  @Query(
      "SELECT l FROM SupplierBillLine l "
          + "LEFT JOIN FETCH l.account "
          + "LEFT JOIN FETCH l.taxCode "
          + "WHERE l.bill = :bill "
          + "ORDER BY l.lineIndex")
  List<SupplierBillLine> findByBillOrderByLineIndex(@Param("bill") SupplierBill bill);

  void deleteByBill(SupplierBill bill);
}
