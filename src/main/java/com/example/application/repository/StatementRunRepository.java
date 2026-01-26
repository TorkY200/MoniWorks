package com.example.application.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Company;
import com.example.application.domain.StatementRun;
import com.example.application.domain.StatementRun.RunStatus;

@Repository
public interface StatementRunRepository extends JpaRepository<StatementRun, Long> {

  @Query(
      "SELECT sr FROM StatementRun sr "
          + "LEFT JOIN FETCH sr.createdBy "
          + "WHERE sr.company = :company "
          + "ORDER BY sr.createdAt DESC")
  List<StatementRun> findByCompanyOrderByCreatedAtDesc(@Param("company") Company company);

  @Query(
      "SELECT sr FROM StatementRun sr "
          + "LEFT JOIN FETCH sr.createdBy "
          + "WHERE sr.company = :company AND sr.status = :status "
          + "ORDER BY sr.createdAt DESC")
  List<StatementRun> findByCompanyAndStatusOrderByCreatedAtDesc(
      @Param("company") Company company, @Param("status") RunStatus status);

  @Query(
      "SELECT r FROM StatementRun r WHERE r.company = :company "
          + "AND r.runDate >= :startDate AND r.runDate <= :endDate "
          + "ORDER BY r.createdAt DESC")
  List<StatementRun> findByCompanyAndDateRange(
      @Param("company") Company company,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  // Count by status
  @Query("SELECT COUNT(r) FROM StatementRun r WHERE r.company = :company AND r.status = :status")
  long countByCompanyAndStatus(
      @Param("company") Company company, @Param("status") RunStatus status);
}
