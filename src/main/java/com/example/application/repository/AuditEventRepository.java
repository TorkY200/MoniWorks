package com.example.application.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

  // Retention policy queries

  /**
   * Deletes audit events for a company that are older than the specified cutoff date. Used by the
   * retention policy enforcement.
   */
  @Modifying
  @Query("DELETE FROM AuditEvent ae WHERE ae.company = :company AND ae.createdAt < :cutoff")
  int deleteByCompanyAndCreatedAtBefore(
      @Param("company") Company company, @Param("cutoff") Instant cutoff);

  /**
   * Deletes audit events for a company that are older than the cutoff date, excluding login/logout
   * events. Used when retention policy is configured to keep login events.
   */
  @Modifying
  @Query(
      "DELETE FROM AuditEvent ae WHERE ae.company = :company "
          + "AND ae.createdAt < :cutoff "
          + "AND ae.eventType NOT IN ('USER_LOGIN', 'USER_LOGOUT', 'LOGIN_FAILED')")
  int deleteByCompanyAndCreatedAtBeforeExcludingLoginEvents(
      @Param("company") Company company, @Param("cutoff") Instant cutoff);

  /**
   * Counts audit events for a company that would be deleted by the retention policy. Useful for
   * preview before cleanup.
   */
  @Query(
      "SELECT COUNT(ae) FROM AuditEvent ae WHERE ae.company = :company AND ae.createdAt < :cutoff")
  long countByCompanyAndCreatedAtBefore(
      @Param("company") Company company, @Param("cutoff") Instant cutoff);

  /** Counts audit events for a company that would be deleted, excluding login events. */
  @Query(
      "SELECT COUNT(ae) FROM AuditEvent ae WHERE ae.company = :company "
          + "AND ae.createdAt < :cutoff "
          + "AND ae.eventType NOT IN ('USER_LOGIN', 'USER_LOGOUT', 'LOGIN_FAILED')")
  long countByCompanyAndCreatedAtBeforeExcludingLoginEvents(
      @Param("company") Company company, @Param("cutoff") Instant cutoff);
}
