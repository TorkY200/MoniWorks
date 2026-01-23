package com.example.application.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.application.domain.SalesInvoice;
import com.example.application.domain.SalesInvoiceLine;

@Repository
public interface SalesInvoiceLineRepository extends JpaRepository<SalesInvoiceLine, Long> {

  List<SalesInvoiceLine> findByInvoiceOrderByLineIndex(SalesInvoice invoice);

  void deleteByInvoice(SalesInvoice invoice);
}
