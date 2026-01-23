package com.example.application.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.Company;
import com.example.application.domain.Product;
import com.example.application.domain.User;
import com.example.application.repository.AccountRepository;
import com.example.application.repository.ProductRepository;

/**
 * Service for importing products from CSV files.
 *
 * <p>Supports flexible column mapping with required fields: - code (required, max 31 chars) - name
 * (required, max 100 chars)
 *
 * <p>Optional fields: description, category, buyPrice, sellPrice, taxCode, barcode, stickyNote,
 * salesAccountCode, purchaseAccountCode
 */
@Service
@Transactional
public class ProductImportService {

  private final ProductRepository productRepository;
  private final AccountRepository accountRepository;
  private final AuditService auditService;

  public ProductImportService(
      ProductRepository productRepository,
      AccountRepository accountRepository,
      AuditService auditService) {
    this.productRepository = productRepository;
    this.accountRepository = accountRepository;
    this.auditService = auditService;
  }

  /** Result of a CSV import operation. */
  public record ImportResult(
      boolean success,
      int imported,
      int updated,
      int skipped,
      List<String> errors,
      List<String> warnings) {
    public static ImportResult success(
        int imported, int updated, int skipped, List<String> warnings) {
      return new ImportResult(true, imported, updated, skipped, List.of(), warnings);
    }

    public static ImportResult failure(List<String> errors) {
      return new ImportResult(false, 0, 0, 0, errors, List.of());
    }
  }

  /**
   * Previews the CSV import without making changes. Returns information about what would be
   * imported/updated.
   */
  public ImportResult previewImport(InputStream csvStream, Company company, boolean updateExisting)
      throws IOException {
    return processImport(csvStream, company, null, updateExisting, true);
  }

  /**
   * Imports products from a CSV file.
   *
   * @param csvStream The CSV file input stream
   * @param company The company to import products into
   * @param importedBy The user performing the import
   * @param updateExisting If true, updates existing products by code; if false, skips duplicates
   * @return ImportResult with counts and any errors/warnings
   */
  public ImportResult importProducts(
      InputStream csvStream, Company company, User importedBy, boolean updateExisting)
      throws IOException {
    return processImport(csvStream, company, importedBy, updateExisting, false);
  }

  private ImportResult processImport(
      InputStream csvStream,
      Company company,
      User importedBy,
      boolean updateExisting,
      boolean previewOnly)
      throws IOException {

    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    int imported = 0;
    int updated = 0;
    int skipped = 0;

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {

      // Read header line
      String headerLine = reader.readLine();
      if (headerLine == null || headerLine.isBlank()) {
        return ImportResult.failure(List.of("CSV file is empty or has no header"));
      }

      String[] headers = parseCsvLine(headerLine);
      Map<String, Integer> columnMap = buildColumnMap(headers);

      // Validate required columns
      if (!columnMap.containsKey("code")) {
        errors.add("Required column 'code' not found in CSV header");
      }
      if (!columnMap.containsKey("name")) {
        errors.add("Required column 'name' not found in CSV header");
      }
      if (!errors.isEmpty()) {
        return ImportResult.failure(errors);
      }

      // Process data rows
      String line;
      int lineNumber = 1;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          continue;
        }

        try {
          String[] values = parseCsvLine(line);
          ProcessRowResult result =
              processRow(values, columnMap, company, updateExisting, previewOnly, lineNumber);

          switch (result.action) {
            case IMPORTED -> imported++;
            case UPDATED -> updated++;
            case SKIPPED -> {
              skipped++;
              if (result.message != null) {
                warnings.add(result.message);
              }
            }
            case ERROR -> {
              skipped++;
              errors.add(result.message);
            }
          }
        } catch (Exception e) {
          errors.add("Line " + lineNumber + ": " + e.getMessage());
          skipped++;
        }
      }
    }

    if (!errors.isEmpty() && imported == 0 && updated == 0) {
      return ImportResult.failure(errors);
    }

    // Log the import
    if (!previewOnly && (imported > 0 || updated > 0)) {
      auditService.logEvent(
          company,
          importedBy,
          "PRODUCTS_IMPORTED",
          "Product",
          null,
          "Imported "
              + imported
              + " new, updated "
              + updated
              + ", skipped "
              + skipped
              + " products from CSV");
    }

    return ImportResult.success(
        imported, updated, skipped, errors.isEmpty() ? warnings : new ArrayList<>(errors));
  }

  private enum RowAction {
    IMPORTED,
    UPDATED,
    SKIPPED,
    ERROR
  }

  private record ProcessRowResult(RowAction action, String message) {}

  private ProcessRowResult processRow(
      String[] values,
      Map<String, Integer> columnMap,
      Company company,
      boolean updateExisting,
      boolean previewOnly,
      int lineNumber) {

    String code = getColumnValue(values, columnMap, "code");
    String name = getColumnValue(values, columnMap, "name");

    // Validate required fields
    if (code == null || code.isBlank()) {
      return new ProcessRowResult(
          RowAction.ERROR, "Line " + lineNumber + ": Missing required field 'code'");
    }
    if (name == null || name.isBlank()) {
      return new ProcessRowResult(
          RowAction.ERROR, "Line " + lineNumber + ": Missing required field 'name'");
    }

    // Validate code length
    if (code.length() > 31) {
      return new ProcessRowResult(
          RowAction.ERROR,
          "Line " + lineNumber + ": Code '" + code + "' exceeds maximum length of 31 characters");
    }

    // Check if product exists
    Product existing = productRepository.findByCompanyAndCode(company, code).orElse(null);

    if (existing != null) {
      if (!updateExisting) {
        return new ProcessRowResult(
            RowAction.SKIPPED,
            "Line " + lineNumber + ": Product with code '" + code + "' already exists");
      }

      if (!previewOnly) {
        populateProduct(existing, values, columnMap, company);
        productRepository.save(existing);
      }
      return new ProcessRowResult(RowAction.UPDATED, null);
    }

    // Create new product
    if (!previewOnly) {
      Product product = new Product(company, code, name);
      populateProduct(product, values, columnMap, company);
      productRepository.save(product);
    }
    return new ProcessRowResult(RowAction.IMPORTED, null);
  }

  private void populateProduct(
      Product product, String[] values, Map<String, Integer> columnMap, Company company) {
    // Name
    String name = getColumnValue(values, columnMap, "name");
    if (name != null && !name.isBlank()) {
      product.setName(truncate(name, 100));
    }

    // Description
    String description = getColumnValue(values, columnMap, "description");
    if (description != null) {
      product.setDescription(truncate(description.trim(), 500));
    }

    // Category
    String category = getColumnValue(values, columnMap, "category");
    if (category != null) {
      product.setCategory(truncate(category.trim(), 50));
    }

    // Buy price (cost)
    String buyPriceStr = getColumnValue(values, columnMap, "buyprice");
    if (buyPriceStr != null && !buyPriceStr.isBlank()) {
      try {
        product.setBuyPrice(parsePrice(buyPriceStr));
      } catch (NumberFormatException e) {
        // Skip invalid price
      }
    }

    // Sell price
    String sellPriceStr = getColumnValue(values, columnMap, "sellprice");
    if (sellPriceStr != null && !sellPriceStr.isBlank()) {
      try {
        product.setSellPrice(parsePrice(sellPriceStr));
      } catch (NumberFormatException e) {
        // Skip invalid price
      }
    }

    // Tax code
    setIfPresent(product::setTaxCode, getColumnValue(values, columnMap, "taxcode"), 10);

    // Barcode
    setIfPresent(product::setBarcode, getColumnValue(values, columnMap, "barcode"), 50);

    // Sticky note
    String stickyNote = getColumnValue(values, columnMap, "stickynote");
    if (stickyNote != null) {
      product.setStickyNote(truncate(stickyNote.trim(), 500));
    }

    // Sales account (by code)
    String salesAccountCode = getColumnValue(values, columnMap, "salesaccountcode");
    if (salesAccountCode != null && !salesAccountCode.isBlank()) {
      accountRepository
          .findByCompanyAndCode(company, salesAccountCode.trim())
          .ifPresent(product::setSalesAccount);
    }

    // Purchase account (by code)
    String purchaseAccountCode = getColumnValue(values, columnMap, "purchaseaccountcode");
    if (purchaseAccountCode != null && !purchaseAccountCode.isBlank()) {
      accountRepository
          .findByCompanyAndCode(company, purchaseAccountCode.trim())
          .ifPresent(product::setPurchaseAccount);
    }
  }

  private BigDecimal parsePrice(String priceStr) {
    // Remove currency symbols, commas, and whitespace
    String cleaned = priceStr.trim().replace("$", "").replace(",", "").replace(" ", "");
    return new BigDecimal(cleaned);
  }

  private void setIfPresent(
      java.util.function.Consumer<String> setter, String value, int maxLength) {
    if (value != null && !value.isBlank()) {
      setter.accept(truncate(value.trim(), maxLength));
    }
  }

  private String truncate(String value, int maxLength) {
    if (value == null) return null;
    return value.length() > maxLength ? value.substring(0, maxLength) : value;
  }

  private Map<String, Integer> buildColumnMap(String[] headers) {
    Map<String, Integer> map = new HashMap<>();
    for (int i = 0; i < headers.length; i++) {
      String header =
          headers[i].toLowerCase().trim().replace(" ", "").replace("_", "").replace("-", "");
      map.put(header, i);
    }
    return map;
  }

  private String getColumnValue(String[] values, Map<String, Integer> columnMap, String column) {
    Integer index = columnMap.get(column);
    if (index == null || index >= values.length) {
      return null;
    }
    String value = values[index];
    return value == null || value.isBlank() ? null : value.trim();
  }

  /** Parses a CSV line, handling quoted fields with commas. */
  private String[] parseCsvLine(String line) {
    List<String> values = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);

      if (c == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          // Escaped quote
          current.append('"');
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (c == ',' && !inQuotes) {
        values.add(current.toString().trim());
        current = new StringBuilder();
      } else {
        current.append(c);
      }
    }
    values.add(current.toString().trim());

    return values.toArray(new String[0]);
  }

  /** Returns sample CSV format for download. */
  public String getSampleCsvContent() {
    return """
            code,name,description,category,buyPrice,sellPrice,taxCode,barcode,salesAccountCode,purchaseAccountCode,stickyNote
            PROD001,Widget A,"Standard widget, blue",Widgets,10.50,25.00,GST,1234567890123,4100,5100,Check stock levels
            PROD002,Widget B,"Deluxe widget, red",Widgets,15.00,35.00,GST,1234567890124,4100,5100,
            SVC001,Consulting,"Hourly consulting rate",Services,,150.00,GST,,4200,,
            """;
  }
}
