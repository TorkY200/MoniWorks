package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByNameAndSystemTrue(String name);

    List<Role> findBySystemTrue();

    List<Role> findByCompany(Company company);

    @Query("SELECT r FROM Role r WHERE r.system = true OR r.company = :company")
    List<Role> findAvailableRolesForCompany(@Param("company") Company company);

    Optional<Role> findByCompanyAndName(Company company, String name);
}
