package com.example.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.Company;
import com.example.application.domain.Product;
import com.example.application.domain.User;
import com.example.application.repository.ProductRepository;

/**
 * Service for managing products and services. Products store pricing defaults and can auto-fill
 * invoice/bill lines.
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
   *
   * @throws IllegalArgumentException if code already exists
   */
  public Product createProduct(Company company, String code, String name) {
    if (productRepository.existsByCompanyAndCode(company, code)) {
      throw new IllegalArgumentException("Product code already exists: " + code);
    }
    Product product = new Product(company, code, name);
    product = productRepository.save(product);

    auditService.logEvent(
        company,
        null,
        "PRODUCT_CREATED",
        "Product",
        product.getId(),
        "Created product: " + code + " - " + name);

    return product;
  }

  /** Finds all products for a company, ordered by code. */
  @Transactional(readOnly = true)
  public List<Product> findByCompany(Company company) {
    return productRepository.findByCompanyOrderByCode(company);
  }

  /** Finds active products for a company, ordered by code. */
  @Transactional(readOnly = true)
  public List<Product> findActiveByCompany(Company company) {
    return productRepository.findByCompanyAndActiveOrderByCode(company, true);
  }

  /** Finds products by category. */
  @Transactional(readOnly = true)
  public List<Product> findByCompanyAndCategory(Company company, String category) {
    return productRepository.findByCompanyAndCategoryOrderByCode(company, category);
  }

  /** Finds inventoried products. */
  @Transactional(readOnly = true)
  public List<Product> findInventoriedByCompany(Company company) {
    return productRepository.findByCompanyAndInventoriedOrderByCode(company, true);
  }

  /** Searches products by code, name, or description. */
  @Transactional(readOnly = true)
  public List<Product> searchByCompany(Company company, String searchTerm) {
    if (searchTerm == null || searchTerm.isBlank()) {
      return findByCompany(company);
    }
    return productRepository.searchByCompany(company, searchTerm.trim());
  }

  /** Finds a product by company and code. */
  @Transactional(readOnly = true)
  public Optional<Product> findByCompanyAndCode(Company company, String code) {
    return productRepository.findByCompanyAndCode(company, code);
  }

  /** Finds a product by barcode. */
  @Transactional(readOnly = true)
  public Optional<Product> findByBarcode(String barcode) {
    return productRepository.findByBarcode(barcode);
  }

  /** Finds a product by ID. */
  @Transactional(readOnly = true)
  public Optional<Product> findById(Long id) {
    return productRepository.findById(id);
  }

  /** Saves a product. */
  public Product save(Product product) {
    return save(product, null);
  }

  /**
   * Saves a product with audit logging for edits. Captures before/after state for key fields.
   *
   * @param product the product to save
   * @param actor the user making the change
   * @return the saved product
   */
  public Product save(Product product, User actor) {
    boolean isNew = product.getId() == null;

    if (!isNew) {
      // Capture before state for existing product
      Product before = productRepository.findById(product.getId()).orElse(null);
      if (before != null) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (!before.getCode().equals(product.getCode())) {
          changes.put("code", Map.of("from", before.getCode(), "to", product.getCode()));
        }
        if (!before.getName().equals(product.getName())) {
          changes.put("name", Map.of("from", before.getName(), "to", product.getName()));
        }
        if (!Objects.equals(before.getDescription(), product.getDescription())) {
          changes.put(
              "description",
              Map.of(
                  "from", before.getDescription() != null ? before.getDescription() : "",
                  "to", product.getDescription() != null ? product.getDescription() : ""));
        }
        if (!Objects.equals(before.getCategory(), product.getCategory())) {
          changes.put(
              "category",
              Map.of(
                  "from", before.getCategory() != null ? before.getCategory() : "",
                  "to", product.getCategory() != null ? product.getCategory() : ""));
        }
        if ((before.getSellPrice() != null
                && product.getSellPrice() != null
                && before.getSellPrice().compareTo(product.getSellPrice()) != 0)
            || (before.getSellPrice() == null) != (product.getSellPrice() == null)) {
          changes.put(
              "sellPrice",
              Map.of(
                  "from", before.getSellPrice() != null ? before.getSellPrice().toString() : "",
                  "to", product.getSellPrice() != null ? product.getSellPrice().toString() : ""));
        }
        if ((before.getBuyPrice() != null
                && product.getBuyPrice() != null
                && before.getBuyPrice().compareTo(product.getBuyPrice()) != 0)
            || (before.getBuyPrice() == null) != (product.getBuyPrice() == null)) {
          changes.put(
              "buyPrice",
              Map.of(
                  "from", before.getBuyPrice() != null ? before.getBuyPrice().toString() : "",
                  "to", product.getBuyPrice() != null ? product.getBuyPrice().toString() : ""));
        }
        if (before.isActive() != product.isActive()) {
          changes.put("active", Map.of("from", before.isActive(), "to", product.isActive()));
        }
        if (!Objects.equals(before.getTaxCode(), product.getTaxCode())) {
          changes.put(
              "taxCode",
              Map.of(
                  "from", before.getTaxCode() != null ? before.getTaxCode() : "",
                  "to", product.getTaxCode() != null ? product.getTaxCode() : ""));
        }

        if (!changes.isEmpty()) {
          Product saved = productRepository.save(product);
          auditService.logEvent(
              product.getCompany(),
              actor,
              "PRODUCT_UPDATED",
              "Product",
              product.getId(),
              "Updated product: " + product.getCode(),
              changes);
          return saved;
        }
      }
    }

    return productRepository.save(product);
  }

  /** Deactivates a product (soft delete). */
  public void deactivate(Product product) {
    product.setActive(false);
    productRepository.save(product);

    auditService.logEvent(
        product.getCompany(),
        null,
        "PRODUCT_DEACTIVATED",
        "Product",
        product.getId(),
        "Deactivated product: " + product.getCode());
  }

  /** Gets distinct categories used by products in a company. */
  @Transactional(readOnly = true)
  public List<String> getCategories(Company company) {
    return productRepository.findDistinctCategoriesByCompany(company);
  }
}
