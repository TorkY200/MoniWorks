package com.example.application.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Account;
import com.example.application.domain.Budget;
import com.example.application.domain.BudgetLine;
import com.example.application.domain.Department;
import com.example.application.domain.Period;

@Repository
public interface BudgetLineRepository extends JpaRepository<BudgetLine, Long> {

  @Query(
      "SELECT bl FROM BudgetLine bl "
          + "JOIN FETCH bl.account "
          + "LEFT JOIN FETCH bl.period "
          + "LEFT JOIN FETCH bl.department "
          + "WHERE bl.budget = :budget")
  List<BudgetLine> findByBudget(@Param("budget") Budget budget);

  List<BudgetLine> findByBudgetAndPeriod(Budget budget, Period period);

  List<BudgetLine> findByBudgetAndAccount(Budget budget, Account account);

  List<BudgetLine> findByBudgetAndDepartment(Budget budget, Department department);

  Optional<BudgetLine> findByBudgetAndPeriodAndAccountAndDepartment(
      Budget budget, Period period, Account account, Department department);

  @Query(
      "SELECT bl FROM BudgetLine bl WHERE bl.budget = :budget "
          + "AND bl.period.fiscalYear.id = :fiscalYearId ORDER BY bl.period.startDate, bl.account.code")
  List<BudgetLine> findByBudgetAndFiscalYear(
      @Param("budget") Budget budget, @Param("fiscalYearId") Long fiscalYearId);

  @Query(
      "SELECT SUM(bl.amount) FROM BudgetLine bl WHERE bl.budget = :budget "
          + "AND bl.account = :account AND bl.period.fiscalYear.id = :fiscalYearId")
  BigDecimal sumByBudgetAndAccountAndFiscalYear(
      @Param("budget") Budget budget,
      @Param("account") Account account,
      @Param("fiscalYearId") Long fiscalYearId);

  @Query(
      "SELECT SUM(bl.amount) FROM BudgetLine bl WHERE bl.budget = :budget "
          + "AND bl.account = :account AND bl.department = :department "
          + "AND bl.period.fiscalYear.id = :fiscalYearId")
  BigDecimal sumByBudgetAndAccountAndDepartmentAndFiscalYear(
      @Param("budget") Budget budget,
      @Param("account") Account account,
      @Param("department") Department department,
      @Param("fiscalYearId") Long fiscalYearId);

  void deleteByBudget(Budget budget);
}
