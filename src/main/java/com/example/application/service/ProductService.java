package com.example.application.service;

import com.example.application.domain.Company;
import com.example.application.domain.Product;
import com.example.application.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing products and services.
 * Products store pricing defaults and can auto-fill invoice/bill lines.
 */
@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final AuditService auditService;

    public ProductService(ProductRepository productRepository, AuditService auditService) {
        this.productRepository = productRepository;
        this.auditService = auditService;
    }

    /**
     * Creates a new product for the given company.
     * @throws IllegalArgumentException if code already exists
     */
    public Product createProduct(Company company, String code, String name) {
        if (productRepository.existsByCompanyAndCode(company, code)) {
            throw new IllegalArgumentException("Product code already exists: " + code);
        }
        Product product = new Product(company, code, name);
        product = productRepository.save(product);

        auditService.logEvent(company, null, "PRODUCT_CREATED", "Product", product.getId(),
            "Created product: " + code + " - " + name);

        return product;
    }

    /**
     * Finds all products for a company, ordered by code.
     */
    @Transactional(readOnly = true)
    public List<Product> findByCompany(Company company) {
        return productRepository.findByCompanyOrderByCode(company);
    }

    /**
     * Finds active products for a company, ordered by code.
     */
    @Transactional(readOnly = true)
    public List<Product> findActiveByCompany(Company company) {
        return productRepository.findByCompanyAndActiveOrderByCode(company, true);
    }

    /**
     * Finds products by category.
     */
    @Transactional(readOnly = true)
    public List<Product> findByCompanyAndCategory(Company company, String category) {
        return productRepository.findByCompanyAndCategoryOrderByCode(company, category);
    }

    /**
     * Finds inventoried products.
     */
    @Transactional(readOnly = true)
    public List<Product> findInventoriedByCompany(Company company) {
        return productRepository.findByCompanyAndInventoriedOrderByCode(company, true);
    }

    /**
     * Searches products by code, name, or description.
     */
    @Transactional(readOnly = true)
    public List<Product> searchByCompany(Company company, String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return findByCompany(company);
        }
        return productRepository.searchByCompany(company, searchTerm.trim());
    }

    /**
     * Finds a product by company and code.
     */
    @Transactional(readOnly = true)
    public Optional<Product> findByCompanyAndCode(Company company, String code) {
        return productRepository.findByCompanyAndCode(company, code);
    }

    /**
     * Finds a product by barcode.
     */
    @Transactional(readOnly = true)
    public Optional<Product> findByBarcode(String barcode) {
        return productRepository.findByBarcode(barcode);
    }

    /**
     * Finds a product by ID.
     */
    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    /**
     * Saves a product.
     */
    public Product save(Product product) {
        return productRepository.save(product);
    }

    /**
     * Deactivates a product (soft delete).
     */
    public void deactivate(Product product) {
        product.setActive(false);
        productRepository.save(product);

        auditService.logEvent(product.getCompany(), null, "PRODUCT_DEACTIVATED", "Product", product.getId(),
            "Deactivated product: " + product.getCode());
    }

    /**
     * Gets distinct categories used by products in a company.
     */
    @Transactional(readOnly = true)
    public List<String> getCategories(Company company) {
        return productRepository.findDistinctCategoriesByCompany(company);
    }
}
