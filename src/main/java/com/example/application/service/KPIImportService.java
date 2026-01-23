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
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.*;
import com.example.application.repository.*;

/**
 * Service for importing KPI values from CSV files.
 *
 * <p>CSV format expects columns:
 *
 * <ul>
 *   <li>kpi_code (required) - KPI code to add values for
 *   <li>period_date (required) - Date within the period (YYYY-MM-DD) or period start date
 *   <li>value (required) - KPI value (numeric)
 *   <li>notes (optional) - Notes for the KPI value
 * </ul>
 *
 * <p>The import updates or creates KPI values as needed for existing KPIs.
 */
@Service
@Transactional
public class KPIImportService {

  private final KPIRepository kpiRepository;
  private final KPIValueRepository kpiValueRepository;
  private final PeriodRepository periodRepository;
  private final AuditService auditService;

  public KPIImportService(
      KPIRepository kpiRepository,
      KPIValueRepository kpiValueRepository,
      PeriodRepository periodRepository,
      AuditService auditService) {
    this.kpiRepository = kpiRepository;
    this.kpiValueRepository = kpiValueRepository;
    this.periodRepository = periodRepository;
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

  /** Previews the CSV import without making changes. */
  public ImportResult previewImport(InputStream csvStream, Company company, boolean updateExisting)
      throws IOException {
    return processImport(csvStream, company, null, updateExisting, true);
  }

  /**
   * Imports KPI values from a CSV file.
   *
   * @param csvStream The CSV file input stream
   * @param company The company to import KPI values for
   * @param importedBy The user performing the import
   * @param updateExisting If true, updates existing KPI values; if false, skips duplicates
   * @return ImportResult with counts and any errors/warnings
   */
  public ImportResult importKPIValues(
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
      if (!columnMap.containsKey("kpicode") && !columnMap.containsKey("kpi")) {
        errors.add("Required column 'kpi_code' or 'kpi' not found in CSV header");
      }
      if (!columnMap.containsKey("perioddate")
          && !columnMap.containsKey("period")
          && !columnMap.containsKey("date")) {
        errors.add("Required column 'period_date', 'period', or 'date' not found in CSV header");
      }
      if (!columnMap.containsKey("value")) {
        errors.add("Required column 'value' not found in CSV header");
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
          "KPI_VALUES_IMPORTED",
          "KPIValue",
          null,
          "Imported "
              + imported
              + " new, updated "
              + updated
              + ", skipped "
              + skipped
              + " KPI values from CSV");
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

    // Get KPI code
    String kpiCode = getColumnValue(values, columnMap, "kpicode");
    if (kpiCode == null) {
      kpiCode = getColumnValue(values, columnMap, "kpi");
    }
    if (kpiCode == null || kpiCode.isBlank()) {
      return new ProcessRowResult(RowAction.ERROR, "Line " + lineNumber + ": Missing KPI code");
    }

    // Find KPI
    KPI kpi = kpiRepository.findByCompanyAndCode(company, kpiCode).orElse(null);
    if (kpi == null) {
      return new ProcessRowResult(
          RowAction.ERROR, "Line " + lineNumber + ": KPI '" + kpiCode + "' not found");
    }

    // Get period date
    String periodDateStr = getColumnValue(values, columnMap, "perioddate");
    if (periodDateStr == null) {
      periodDateStr = getColumnValue(values, columnMap, "period");
    }
    if (periodDateStr == null) {
      periodDateStr = getColumnValue(values, columnMap, "date");
    }
    if (periodDateStr == null || periodDateStr.isBlank()) {
      return new ProcessRowResult(RowAction.ERROR, "Line " + lineNumber + ": Missing period date");
    }

    // Parse date
    LocalDate periodDate;
    try {
      periodDate = parseDate(periodDateStr);
    } catch (DateTimeParseException e) {
      return new ProcessRowResult(
          RowAction.ERROR, "Line " + lineNumber + ": Invalid date format '" + periodDateStr + "'");
    }

    // Find period
    Period period = periodRepository.findByCompanyAndDate(company, periodDate).orElse(null);
    if (period == null) {
      return new ProcessRowResult(
          RowAction.ERROR, "Line " + lineNumber + ": No period found for date " + periodDate);
    }

    // Get value
    String valueStr = getColumnValue(values, columnMap, "value");
    if (valueStr == null || valueStr.isBlank()) {
      return new ProcessRowResult(RowAction.ERROR, "Line " + lineNumber + ": Missing value");
    }

    BigDecimal value;
    try {
      value = new BigDecimal(valueStr.trim().replace(",", "").replace("$", ""));
    } catch (NumberFormatException e) {
      return new ProcessRowResult(
          RowAction.ERROR, "Line " + lineNumber + ": Invalid value '" + valueStr + "'");
    }

    // Get optional notes
    String notes = getColumnValue(values, columnMap, "notes");
    if (notes == null) {
      notes = getColumnValue(values, columnMap, "note");
    }
    // Truncate notes if too long
    if (notes != null && notes.length() > 255) {
      notes = notes.substring(0, 255);
    }

    // Check if KPI value exists for this KPI and period
    Optional<KPIValue> existing = kpiValueRepository.findByKpiAndPeriod(kpi, period);

    if (existing.isPresent()) {
      if (!updateExisting) {
        return new ProcessRowResult(
            RowAction.SKIPPED,
            "Line "
                + lineNumber
                + ": KPI value already exists for "
                + kpiCode
                + " in period "
                + period.getStartDate());
      }

      if (!previewOnly) {
        KPIValue kpiValue = existing.get();
        kpiValue.setValue(value);
        if (notes != null) {
          kpiValue.setNotes(notes);
        }
        kpiValueRepository.save(kpiValue);
      }
      return new ProcessRowResult(RowAction.UPDATED, null);
    }

    // Create new KPI value
    if (!previewOnly) {
      KPIValue kpiValue = new KPIValue();
      kpiValue.setKpi(kpi);
      kpiValue.setPeriod(period);
      kpiValue.setValue(value);
      if (notes != null) {
        kpiValue.setNotes(notes);
      }
      kpiValueRepository.save(kpiValue);
    }
    return new ProcessRowResult(RowAction.IMPORTED, null);
  }

  private LocalDate parseDate(String dateStr) {
    // Try common date formats
    String[] patterns = {"yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "yyyy/MM/dd"};

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
            kpi_code,period_date,value,notes
            REV,2024-07-01,150000,Strong Q3 start
            REV,2024-08-01,165000,Peak summer sales
            CUST,2024-07-01,2500,Active customers count
            CUST,2024-08-01,2750,Growth tracking well
            NPS,2024-07-01,75,Excellent feedback
            NPS,2024-08-01,78,Improving trend
            """;
  }
}
