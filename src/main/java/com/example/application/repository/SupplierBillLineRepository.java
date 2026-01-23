package com.example.application.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.application.domain.SupplierBill;
import com.example.application.domain.SupplierBillLine;

@Repository
public interface SupplierBillLineRepository extends JpaRepository<SupplierBillLine, Long> {

  List<SupplierBillLine> findByBillOrderByLineIndex(SupplierBill bill);

  void deleteByBill(SupplierBill bill);
}
