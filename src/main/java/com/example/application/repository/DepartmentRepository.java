package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByCompanyOrderByCode(Company company);

    List<Department> findByCompanyAndActiveOrderByCode(Company company, boolean active);

    Optional<Department> findByCompanyAndCode(Company company, String code);

    boolean existsByCompanyAndCode(Company company, String code);
}
