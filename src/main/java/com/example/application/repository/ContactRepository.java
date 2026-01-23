package com.example.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Company;
import com.example.application.domain.Contact;
import com.example.application.domain.Contact.ContactType;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

  List<Contact> findByCompanyOrderByCode(Company company);

  List<Contact> findByCompanyAndActiveOrderByCode(Company company, boolean active);

  List<Contact> findByCompanyAndTypeOrderByCode(Company company, ContactType type);

  List<Contact> findByCompanyAndTypeAndActiveOrderByCode(
      Company company, ContactType type, boolean active);

  Optional<Contact> findByCompanyAndCode(Company company, String code);

  boolean existsByCompanyAndCode(Company company, String code);

  @Query(
      "SELECT c FROM Contact c WHERE c.company = :company AND "
          + "(c.type = :type OR c.type = 'BOTH') AND c.active = true ORDER BY c.name")
  List<Contact> findActiveByCompanyAndTypeOrBoth(
      @Param("company") Company company, @Param("type") ContactType type);

  @Query(
      "SELECT c FROM Contact c WHERE c.company = :company AND "
          + "(LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%')) OR "
          + "LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR "
          + "LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))) "
          + "ORDER BY c.name")
  List<Contact> searchByCompany(@Param("company") Company company, @Param("search") String search);

  @Query(
      "SELECT DISTINCT c.category FROM Contact c WHERE c.company = :company AND c.category IS NOT NULL ORDER BY c.category")
  List<String> findDistinctCategoriesByCompany(@Param("company") Company company);
}
