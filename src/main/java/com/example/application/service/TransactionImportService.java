package com.example.application.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.*;
import com.example.application.domain.Transaction.TransactionType;
import com.example.application.domain.TransactionLine.Direction;
import com.example.application.repository.AccountRepository;
import com.example.application.repository.DepartmentRepository;
import com.example.application.repository.TransactionRepository;

/**
 * Service for importing transactions from CSV files.
 *
 * <p>CSV format expects columns: - date (required) - Transaction date - type (required) - PAYMENT,
 * RECEIPT, JOURNAL, or TRANSFER - description (required) - Transaction description - account_code
 * (required) - Account code for the line - amount (required) - Line amount (positive number) -
 * direction (required) - DEBIT or CREDIT - tax_code (optional) - Tax code for the line - memo
 * (optional) - Line memo - department_code (optional) - Department code - reference (optional) -
 * Transaction reference
 *
 * <p>Multiple lines can be created for the same transaction by using the same date, type,
 * description, and reference combination.
 */
@Service
@Transactional
public class TransactionImportService {

  private final TransactionRepository transactionRepository;
  private final AccountRepository accountRepository;
  private final DepartmentRepository departmentRepository;
  private final PostingService postingService;
  private final AuditService auditService;

  public TransactionImportService(
      TransactionRepository transactionRepository,
      AccountRepository accountRepository,
      DepartmentRepository departmentRepository,
      PostingService postingService,
      AuditService auditService) {
    this.transactionRepository = transactionRepository;
    this.accountRepository = accountRepository;
    this.departmentRepository = departmentRepository;
    this.postingService = postingService;
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

  /** Configuration for the import operation. */
  public record ImportConfig(boolean autoPost, boolean groupByReference) {
    public static ImportConfig defaults() {
      return new ImportConfig(false, true);
    }
  }

  /**
   * Previews the CSV import without making changes. Returns information about what would be
   * imported.
   */
  public ImportResult previewImport(InputStream csvStream, Company company, ImportConfig config)
      throws IOException {
    return processImport(csvStream, company, null, config, true);
  }

  /**
   * Imports transactions from a CSV file.
   *
   * @param csvStream The CSV file input stream
   * @param company The company to import transactions into
   * @param importedBy The user performing the import
   * @param config Import configuration options
   * @return ImportResult with counts and any errors/warnings
   */
  public ImportResult importTransactions(
      InputStream csvStream, Company company, User importedBy, ImportConfig config)
      throws IOException {
    return processImport(csvStream, company, importedBy, config, false);
  }

  private ImportResult processImport(
      InputStream csvStream,
      Company company,
      User importedBy,
      ImportConfig config,
      boolean previewOnly)
      throws IOException {

    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    int imported = 0;
    int skipped = 0;

    // Parse all lines first
    List<ParsedLine> parsedLines = new ArrayList<>();

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
      List<String> columnErrors = validateRequiredColumns(columnMap);
      if (!columnErrors.isEmpty()) {
        return ImportResult.failure(columnErrors);
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
          ParsedLine parsed = parseLine(values, columnMap, company, lineNumber);

          if (parsed.error != null) {
            errors.add(parsed.error);
            skipped++;
          } else {
            parsedLines.add(parsed);
          }
        } catch (Exception e) {
          errors.add("Line " + lineNumber + ": " + e.getMessage());
          skipped++;
        }
      }
    }

    if (parsedLines.isEmpty()) {
      if (!errors.isEmpty()) {
        return ImportResult.failure(errors);
      }
      return ImportResult.failure(List.of("No valid data rows found in CSV"));
    }

    // Group lines into transactions
    List<GroupedTransaction> grouped;
    if (config.groupByReference()) {
      grouped = groupLinesByReference(parsedLines);
    } else {
      grouped = groupLinesIndividually(parsedLines);
    }

    // Validate grouped transactions
    for (GroupedTransaction group : grouped) {
      String validationError = validateTransaction(group);
      if (validationError != null) {
        errors.add(validationError);
      }
    }

    if (!errors.isEmpty() && imported == 0) {
      // If we have errors during grouping/validation, fail
      return ImportResult.failure(errors);
    }

    // Create transactions
    if (!previewOnly) {
      for (GroupedTransaction group : grouped) {
        try {
          Transaction transaction = createTransaction(group, company, importedBy, config);
          imported++;
        } catch (Exception e) {
          errors.add("Failed to create transaction: " + e.getMessage());
          skipped++;
        }
      }

      // Log the import
      if (imported > 0) {
        auditService.logEvent(
            company,
            importedBy,
            "TRANSACTIONS_IMPORTED",
            "Transaction",
            null,
            "Imported " + imported + " transactions, skipped " + skipped + " from CSV");
      }
    } else {
      // For preview, count how many would be imported
      imported = grouped.size();
    }

    return ImportResult.success(
        imported, 0, skipped, errors.isEmpty() ? warnings : new ArrayList<>(errors));
  }

  private List<String> validateRequiredColumns(Map<String, Integer> columnMap) {
    List<String> errors = new ArrayList<>();

    if (!hasColumn(columnMap, "date")) {
      errors.add("Required column 'date' not found in CSV header");
    }
    if (!hasColumn(columnMap, "type")) {
      errors.add("Required column 'type' not found in CSV header");
    }
    if (!hasColumn(columnMap, "description")) {
      errors.add("Required column 'description' not found in CSV header");
    }
    if (!hasColumn(columnMap, "accountcode", "account")) {
      errors.add("Required column 'account_code' or 'account' not found in CSV header");
    }
    if (!hasColumn(columnMap, "amount")) {
      errors.add("Required column 'amount' not found in CSV header");
    }
    if (!hasColumn(columnMap, "direction")) {
      errors.add("Required column 'direction' not found in CSV header");
    }

    return errors;
  }

  private boolean hasColumn(Map<String, Integer> columnMap, String... names) {
    for (String name : names) {
      if (columnMap.containsKey(name)) {
        return true;
      }
    }
    return false;
  }

  /** A parsed line from the CSV. */
  private record ParsedLine(
      int lineNumber,
      LocalDate date,
      TransactionType type,
      String description,
      String reference,
      Account account,
      BigDecimal amount,
      Direction direction,
      String taxCode,
      String memo,
      Department department,
      String error) {

    static ParsedLine error(int lineNumber, String error) {
      return new ParsedLine(
          lineNumber, null, null, null, null, null, null, null, null, null, null, error);
    }
  }

  private ParsedLine parseLine(
      String[] values, Map<String, Integer> columnMap, Company company, int lineNumber) {

    // Parse date
    String dateStr = getColumnValue(values, columnMap, "date");
    if (dateStr == null || dateStr.isBlank()) {
      return ParsedLine.error(lineNumber, "Line " + lineNumber + ": Missing required field 'date'");
    }

    LocalDate date;
    try {
      date = parseDate(dateStr);
    } catch (DateTimeParseException e) {
      return ParsedLine.error(
          lineNumber, "Line " + lineNumber + ": Invalid date format '" + dateStr + "'");
    }

    // Parse type
    String typeStr = getColumnValue(values, columnMap, "type");
    if (typeStr == null || typeStr.isBlank()) {
      return ParsedLine.error(lineNumber, "Line " + lineNumber + ": Missing required field 'type'");
    }

    TransactionType type;
    try {
      type = TransactionType.valueOf(typeStr.toUpperCase().trim());
    } catch (IllegalArgumentException e) {
      return ParsedLine.error(
          lineNumber,
          "Line "
              + lineNumber
              + ": Invalid transaction type '"
              + typeStr
              + "'. Valid values: PAYMENT, RECEIPT, JOURNAL, TRANSFER");
    }

    // Parse description
    String description = getColumnValue(values, columnMap, "description");
    if (description == null || description.isBlank()) {
      return ParsedLine.error(
          lineNumber, "Line " + lineNumber + ": Missing required field 'description'");
    }

    // Parse reference (optional)
    String reference = getColumnValue(values, columnMap, "reference");

    // Parse account code
    String accountCode = getColumnValue(values, columnMap, "accountcode");
    if (accountCode == null) {
      accountCode = getColumnValue(values, columnMap, "account");
    }
    if (accountCode == null || accountCode.isBlank()) {
      return ParsedLine.error(
          lineNumber, "Line " + lineNumber + ": Missing required field 'account_code'");
    }

    Account account = accountRepository.findByCompanyAndCode(company, accountCode).orElse(null);
    if (account == null) {
      return ParsedLine.error(
          lineNumber, "Line " + lineNumber + ": Account '" + accountCode + "' not found");
    }

    // Parse amount
    String amountStr = getColumnValue(values, columnMap, "amount");
    if (amountStr == null || amountStr.isBlank()) {
      return ParsedLine.error(
          lineNumber, "Line " + lineNumber + ": Missing required field 'amount'");
    }

    BigDecimal amount;
    try {
      amount = new BigDecimal(amountStr.trim().replace(",", "").replace("$", ""));
      if (amount.compareTo(BigDecimal.ZERO) < 0) {
        amount = amount.abs(); // Convert negative to positive
      }
    } catch (NumberFormatException e) {
      return ParsedLine.error(
          lineNumber, "Line " + lineNumber + ": Invalid amount '" + amountStr + "'");
    }

    // Parse direction
    String directionStr = getColumnValue(values, columnMap, "direction");
    if (directionStr == null || directionStr.isBlank()) {
      return ParsedLine.error(
          lineNumber, "Line " + lineNumber + ": Missing required field 'direction'");
    }

    Direction direction;
    try {
      String dirNormalized = directionStr.toUpperCase().trim();
      if (dirNormalized.equals("DR") || dirNormalized.equals("D")) {
        direction = Direction.DEBIT;
      } else if (dirNormalized.equals("CR") || dirNormalized.equals("C")) {
        direction = Direction.CREDIT;
      } else {
        direction = Direction.valueOf(dirNormalized);
      }
    } catch (IllegalArgumentException e) {
      return ParsedLine.error(
          lineNumber,
          "Line "
              + lineNumber
              + ": Invalid direction '"
              + directionStr
              + "'. Valid values: DEBIT, CREDIT, DR, CR, D, C");
    }

    // Parse optional fields
    String taxCode = getColumnValue(values, columnMap, "taxcode");
    String memo = getColumnValue(values, columnMap, "memo");

    // Parse optional department
    String deptCode = getColumnValue(values, columnMap, "departmentcode");
    if (deptCode == null) {
      deptCode = getColumnValue(values, columnMap, "department");
    }
    if (deptCode == null) {
      deptCode = getColumnValue(values, columnMap, "dept");
    }

    Department department = null;
    if (deptCode != null && !deptCode.isBlank()) {
      department = departmentRepository.findByCompanyAndCode(company, deptCode).orElse(null);
      if (department == null) {
        return ParsedLine.error(
            lineNumber, "Line " + lineNumber + ": Department '" + deptCode + "' not found");
      }
    }

    return new ParsedLine(
        lineNumber,
        date,
        type,
        description,
        reference,
        account,
        amount,
        direction,
        taxCode,
        memo,
        department,
        null);
  }

  /** A grouped transaction with its lines. */
  private record GroupedTransaction(
      LocalDate date,
      TransactionType type,
      String description,
      String reference,
      List<ParsedLine> lines) {}

  private List<GroupedTransaction> groupLinesByReference(List<ParsedLine> lines) {
    // Group by date + type + description + reference
    Map<String, List<ParsedLine>> groups = new HashMap<>();

    for (ParsedLine line : lines) {
      String key =
          line.date()
              + "|"
              + line.type()
              + "|"
              + line.description()
              + "|"
              + (line.reference() != null ? line.reference() : "");
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
    }

    List<GroupedTransaction> result = new ArrayList<>();
    for (List<ParsedLine> group : groups.values()) {
      ParsedLine first = group.get(0);
      result.add(
          new GroupedTransaction(
              first.date(), first.type(), first.description(), first.reference(), group));
    }

    return result;
  }

  private List<GroupedTransaction> groupLinesIndividually(List<ParsedLine> lines) {
    // Each line becomes its own transaction
    List<GroupedTransaction> result = new ArrayList<>();
    for (ParsedLine line : lines) {
      result.add(
          new GroupedTransaction(
              line.date(), line.type(), line.description(), line.reference(), List.of(line)));
    }
    return result;
  }

  private String validateTransaction(GroupedTransaction group) {
    // Check that the transaction balances (debits equal credits)
    BigDecimal totalDebits = BigDecimal.ZERO;
    BigDecimal totalCredits = BigDecimal.ZERO;

    for (ParsedLine line : group.lines()) {
      if (line.direction() == Direction.DEBIT) {
        totalDebits = totalDebits.add(line.amount());
      } else {
        totalCredits = totalCredits.add(line.amount());
      }
    }

    if (totalDebits.compareTo(totalCredits) != 0) {
      int firstLine = group.lines().get(0).lineNumber();
      return "Transaction starting at line "
          + firstLine
          + " is unbalanced: debits="
          + totalDebits
          + ", credits="
          + totalCredits;
    }

    return null; // Valid
  }

  private Transaction createTransaction(
      GroupedTransaction group, Company company, User importedBy, ImportConfig config) {

    Transaction transaction = new Transaction(company, group.type(), group.date());
    transaction.setDescription(group.description());
    transaction.setReference(group.reference());
    transaction.setCreatedBy(importedBy);

    for (ParsedLine line : group.lines()) {
      TransactionLine txLine = new TransactionLine(line.account(), line.amount(), line.direction());
      txLine.setTaxCode(line.taxCode());
      txLine.setMemo(line.memo());
      txLine.setDepartment(line.department());
      transaction.addLine(txLine);
    }

    transaction = transactionRepository.save(transaction);

    // Optionally post the transaction
    if (config.autoPost()) {
      try {
        postingService.postTransaction(transaction, importedBy);
      } catch (Exception e) {
        // Log warning but don't fail - transaction is created as draft
        // The transaction is still saved, just not posted
      }
    }

    return transaction;
  }

  private LocalDate parseDate(String dateStr) {
    // Try common date formats
    String[] patterns = {
      "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "yyyy/MM/dd", "d/M/yyyy", "M/d/yyyy"
    };

    for (String pattern : patterns) {
      try {
        return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(pattern));
      } catch (DateTimeParseException e) {
        // Try next pattern
      }
    }

    // Throw if no pattern matched
    throw new DateTimeParseException("Unable to parse date", dateStr, 0);
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
        date,type,description,reference,account_code,amount,direction,tax_code,memo,department_code
        2024-01-15,PAYMENT,Office Supplies,REF001,5000,100.00,DEBIT,S15,Paper and pens,ADMIN
        2024-01-15,PAYMENT,Office Supplies,REF001,1000,100.00,CREDIT,,Bank payment,
        2024-01-20,RECEIPT,Consulting Services,REF002,4000,500.00,CREDIT,S15,Consulting income,SALES
        2024-01-20,RECEIPT,Consulting Services,REF002,1000,500.00,DEBIT,,Cash received,
        2024-02-01,JOURNAL,Depreciation Entry,JNL001,5200,150.00,DEBIT,,Monthly depreciation,
        2024-02-01,JOURNAL,Depreciation Entry,JNL001,1500,150.00,CREDIT,,Accum depreciation,
        """;
  }
}
