package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.TaxCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxCodeRepository extends JpaRepository<TaxCode, Long> {

    List<TaxCode> findByCompanyOrderByCode(Company company);

    List<TaxCode> findByCompanyAndActiveOrderByCode(Company company, boolean active);

    Optional<TaxCode> findByCompanyAndCode(Company company, String code);

    boolean existsByCompanyAndCode(Company company, String code);
}
