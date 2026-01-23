package com.example.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Company;
import com.example.application.domain.TaxCode;

@Repository
public interface TaxCodeRepository extends JpaRepository<TaxCode, Long> {

  List<TaxCode> findByCompanyOrderByCode(Company company);

  List<TaxCode> findByCompanyAndActiveOrderByCode(Company company, boolean active);

  Optional<TaxCode> findByCompanyAndCode(Company company, String code);

  boolean existsByCompanyAndCode(Company company, String code);
}
