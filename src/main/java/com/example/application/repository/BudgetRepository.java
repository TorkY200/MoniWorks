package com.example.application.repository;

import com.example.application.domain.Budget;
import com.example.application.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByCompanyOrderByName(Company company);

    List<Budget> findByCompanyAndActiveOrderByName(Company company, boolean active);

    List<Budget> findByCompanyAndTypeOrderByName(Company company, Budget.BudgetType type);

    Optional<Budget> findByCompanyAndName(Company company, String name);

    boolean existsByCompanyAndName(Company company, String name);
}
