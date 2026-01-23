package com.example.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Account;
import com.example.application.domain.BankStatementImport;
import com.example.application.domain.Company;

@Repository
public interface BankStatementImportRepository extends JpaRepository<BankStatementImport, Long> {

  List<BankStatementImport> findByCompanyOrderByImportedAtDesc(Company company);

  List<BankStatementImport> findByAccountOrderByImportedAtDesc(Account account);

  Optional<BankStatementImport> findByCompanyAndAccountAndFileHash(
      Company company, Account account, String fileHash);

  boolean existsByCompanyAndAccountAndFileHash(Company company, Account account, String fileHash);
}
