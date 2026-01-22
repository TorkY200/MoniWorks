package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.KPI;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KPIRepository extends JpaRepository<KPI, Long> {

    List<KPI> findByCompanyOrderByCode(Company company);

    List<KPI> findByCompanyAndActiveOrderByCode(Company company, boolean active);

    Optional<KPI> findByCompanyAndCode(Company company, String code);

    boolean existsByCompanyAndCode(Company company, String code);
}
