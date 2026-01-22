package com.example.application.repository;

import com.example.application.domain.Company;
import com.example.application.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCompanyOrderByCode(Company company);

    List<Product> findByCompanyAndActiveOrderByCode(Company company, boolean active);

    List<Product> findByCompanyAndCategoryOrderByCode(Company company, String category);

    List<Product> findByCompanyAndInventoriedOrderByCode(Company company, boolean inventoried);

    Optional<Product> findByCompanyAndCode(Company company, String code);

    Optional<Product> findByBarcode(String barcode);

    boolean existsByCompanyAndCode(Company company, String code);

    @Query("SELECT p FROM Product p WHERE p.company = :company AND " +
           "(LOWER(p.code) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY p.code")
    List<Product> searchByCompany(@Param("company") Company company, @Param("search") String search);

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.company = :company AND p.category IS NOT NULL ORDER BY p.category")
    List<String> findDistinctCategoriesByCompany(@Param("company") Company company);
}
