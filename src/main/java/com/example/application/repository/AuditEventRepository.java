package com.example.application.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.AuditEvent;
import com.example.application.domain.Company;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

  Page<AuditEvent> findByCompanyOrderByCreatedAtDesc(Company company, Pageable pageable);

  @Query(
      "SELECT ae FROM AuditEvent ae WHERE ae.company = :company "
          + "AND ae.createdAt BETWEEN :start AND :end ORDER BY ae.createdAt DESC")
  List<AuditEvent> findByCompanyAndDateRange(
      @Param("company") Company company, @Param("start") Instant start, @Param("end") Instant end);

  List<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
      String entityType, Long entityId);

  @Query(
      "SELECT ae FROM AuditEvent ae WHERE ae.company = :company "
          + "AND ae.eventType = :eventType ORDER BY ae.createdAt DESC")
  List<AuditEvent> findByCompanyAndEventType(
      @Param("company") Company company, @Param("eventType") String eventType);
}
