package com.example.application.repository;

import com.example.application.domain.AllocationRule;
import com.example.application.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AllocationRuleRepository extends JpaRepository<AllocationRule, Long> {

    List<AllocationRule> findByCompanyOrderByPriorityDesc(Company company);

    @Query("SELECT r FROM AllocationRule r WHERE r.company = :company AND r.enabled = true " +
           "ORDER BY r.priority DESC")
    List<AllocationRule> findEnabledByCompanyOrderByPriority(@Param("company") Company company);

    List<AllocationRule> findByCompanyAndEnabledOrderByPriorityDesc(Company company, boolean enabled);
}
