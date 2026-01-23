package com.example.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.Company;
import com.example.application.domain.Department;
import com.example.application.domain.User;
import com.example.application.repository.DepartmentRepository;

/**
 * Service for managing departments (cost centres). Departments provide dimensional reporting on
 * transactions.
 */
@Service
@Transactional
public class DepartmentService {

  private final DepartmentRepository departmentRepository;
  private final AuditService auditService;

  public DepartmentService(DepartmentRepository departmentRepository, AuditService auditService) {
    this.departmentRepository = departmentRepository;
    this.auditService = auditService;
  }

  /**
   * Creates a new department for the given company.
   *
   * @throws IllegalArgumentException if code already exists
   */
  public Department createDepartment(Company company, String code, String name, User actor) {
    if (departmentRepository.existsByCompanyAndCode(company, code)) {
      throw new IllegalArgumentException("Department code already exists: " + code);
    }
    Department department = new Department(company, code, name);
    department = departmentRepository.save(department);

    auditService.logEvent(
        company,
        actor,
        "DEPARTMENT_CREATE",
        "Department",
        department.getId(),
        "Created department: " + code + " - " + name);

    return department;
  }

  /** Finds all departments for a company, ordered by code. */
  @Transactional(readOnly = true)
  public List<Department> findByCompany(Company company) {
    return departmentRepository.findByCompanyOrderByCode(company);
  }

  /** Finds active departments for a company, ordered by code. */
  @Transactional(readOnly = true)
  public List<Department> findActiveByCompany(Company company) {
    return departmentRepository.findByCompanyAndActiveOrderByCode(company, true);
  }

  /** Finds a department by company and code. */
  @Transactional(readOnly = true)
  public Optional<Department> findByCompanyAndCode(Company company, String code) {
    return departmentRepository.findByCompanyAndCode(company, code);
  }

  /** Finds a department by ID. */
  @Transactional(readOnly = true)
  public Optional<Department> findById(Long id) {
    return departmentRepository.findById(id);
  }

  /** Saves a department with audit logging for updates. */
  public Department save(Department department, User actor) {
    boolean isNew = department.getId() == null;
    Department saved = departmentRepository.save(department);

    if (!isNew) {
      auditService.logEvent(
          department.getCompany(),
          actor,
          "DEPARTMENT_UPDATE",
          "Department",
          saved.getId(),
          "Updated department: " + saved.getCode() + " - " + saved.getName(),
          Map.of("code", saved.getCode(), "name", saved.getName(), "active", saved.isActive()));
    }

    return saved;
  }

  /** Deactivates a department (soft delete). */
  public void deactivate(Department department, User actor) {
    department.setActive(false);
    departmentRepository.save(department);

    auditService.logEvent(
        department.getCompany(),
        actor,
        "DEPARTMENT_DEACTIVATE",
        "Department",
        department.getId(),
        "Deactivated department: " + department.getCode());
  }

  /** Activates a department. */
  public void activate(Department department, User actor) {
    department.setActive(true);
    departmentRepository.save(department);

    auditService.logEvent(
        department.getCompany(),
        actor,
        "DEPARTMENT_ACTIVATE",
        "Department",
        department.getId(),
        "Activated department: " + department.getCode());
  }

  /**
   * Creates default departments for a new company. Called during company setup to provide sensible
   * defaults.
   */
  public void createDefaultDepartments(Company company, User actor) {
    // Default departments for small businesses
    createDepartment(company, "GEN", "General", actor);
    createDepartment(company, "ADMIN", "Administration", actor);
    createDepartment(company, "SALES", "Sales", actor);
    createDepartment(company, "OPS", "Operations", actor);
  }
}
