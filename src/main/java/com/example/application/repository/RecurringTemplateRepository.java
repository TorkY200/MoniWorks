package com.example.application.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.RecurringTemplate;
import com.example.application.domain.RecurringTemplate.Status;
import com.example.application.domain.RecurringTemplate.TemplateType;

@Repository
public interface RecurringTemplateRepository extends JpaRepository<RecurringTemplate, Long> {

  @Query(
      "SELECT rt FROM RecurringTemplate rt "
          + "LEFT JOIN FETCH rt.contact "
          + "WHERE rt.company.id = :companyId "
          + "ORDER BY rt.name ASC")
  List<RecurringTemplate> findByCompanyIdOrderByNameAsc(@Param("companyId") Long companyId);

  List<RecurringTemplate> findByCompanyIdAndStatusOrderByNameAsc(Long companyId, Status status);

  List<RecurringTemplate> findByCompanyIdAndTemplateTypeOrderByNameAsc(
      Long companyId, TemplateType templateType);

  @Query(
      "SELECT rt FROM RecurringTemplate rt "
          + "LEFT JOIN FETCH rt.contact "
          + "WHERE rt.company.id = :companyId "
          + "AND rt.status = :status AND rt.nextRunDate <= :date ORDER BY rt.nextRunDate")
  List<RecurringTemplate> findDueTemplates(
      @Param("companyId") Long companyId,
      @Param("status") Status status,
      @Param("date") LocalDate date);

  @Query(
      "SELECT rt FROM RecurringTemplate rt WHERE rt.status = :status "
          + "AND rt.nextRunDate <= :date ORDER BY rt.nextRunDate")
  List<RecurringTemplate> findAllDueTemplates(
      @Param("status") Status status, @Param("date") LocalDate date);

  @Query(
      "SELECT rt FROM RecurringTemplate rt WHERE rt.company.id = :companyId "
          + "AND LOWER(rt.name) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY rt.name")
  List<RecurringTemplate> searchByName(
      @Param("companyId") Long companyId, @Param("search") String search);

  @Query(
      "SELECT COUNT(rt) FROM RecurringTemplate rt WHERE rt.company.id = :companyId "
          + "AND rt.status = :status AND rt.nextRunDate <= :date")
  long countDueTemplates(
      @Param("companyId") Long companyId,
      @Param("status") Status status,
      @Param("date") LocalDate date);

  boolean existsByCompanyIdAndName(Long companyId, String name);
}
