package com.example.application.repository;

import com.example.application.domain.Account;
import com.example.application.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByCompanyOrderByCode(Company company);

    List<Account> findByCompanyAndActiveOrderByCode(Company company, boolean active);

    Optional<Account> findByCompanyAndCode(Company company, String code);

    boolean existsByCompanyAndCode(Company company, String code);

    @Query("SELECT a FROM Account a WHERE a.company = :company AND a.parent IS NULL ORDER BY a.code")
    List<Account> findRootAccountsByCompany(@Param("company") Company company);

    List<Account> findByParent(Account parent);

    @Query("SELECT a FROM Account a WHERE a.company.id = :companyId AND a.type = :type ORDER BY a.code")
    List<Account> findByCompanyIdAndType(@Param("companyId") Long companyId,
                                         @Param("type") Account.AccountType type);

    @Query("SELECT a FROM Account a WHERE a.company = :company AND a.bankAccount = true AND a.active = true ORDER BY a.code")
    List<Account> findBankAccountsByCompany(@Param("company") Company company);
}
