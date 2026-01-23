package com.example.application.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Company;
import com.example.application.domain.FiscalYear;

@Repository
public interface FiscalYearRepository extends JpaRepository<FiscalYear, Long> {

  List<FiscalYear> findByCompanyOrderByStartDate(Company company);

  @Query(
      "SELECT fy FROM FiscalYear fy WHERE fy.company = :company "
          + "AND :date BETWEEN fy.startDate AND fy.endDate")
  Optional<FiscalYear> findByCompanyAndDate(
      @Param("company") Company company, @Param("date") LocalDate date);

  @Query(
      "SELECT fy FROM FiscalYear fy WHERE fy.company = :company "
          + "ORDER BY fy.startDate DESC LIMIT 1")
  Optional<FiscalYear> findLatestByCompany(@Param("company") Company company);
}
