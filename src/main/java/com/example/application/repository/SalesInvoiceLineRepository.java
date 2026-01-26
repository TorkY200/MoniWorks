package com.example.application.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.SalesInvoice;
import com.example.application.domain.SalesInvoiceLine;

@Repository
public interface SalesInvoiceLineRepository extends JpaRepository<SalesInvoiceLine, Long> {

  @Query(
      "SELECT l FROM SalesInvoiceLine l "
          + "LEFT JOIN FETCH l.account "
          + "LEFT JOIN FETCH l.taxCode "
          + "WHERE l.invoice = :invoice "
          + "ORDER BY l.lineIndex")
  List<SalesInvoiceLine> findByInvoiceOrderByLineIndex(@Param("invoice") SalesInvoice invoice);

  void deleteByInvoice(SalesInvoice invoice);
}
