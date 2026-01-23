package com.example.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.domain.*;
import com.example.application.repository.*;
import com.example.application.service.KPIImportService.ImportResult;

/** Unit tests for KPIImportService CSV import functionality. */
@ExtendWith(MockitoExtension.class)
class KPIImportServiceTest {

  @Mock private KPIRepository kpiRepository;

  @Mock private KPIValueRepository kpiValueRepository;

  @Mock private PeriodRepository periodRepository;

  @Mock private AuditService auditService;

  private KPIImportService importService;

  private Company company;
  private User user;
  private KPI revenueKpi;
  private Period period;

  @BeforeEach
  void setUp() {
    importService =
        new KPIImportService(kpiRepository, kpiValueRepository, periodRepository, auditService);

    company = new Company();
    company.setId(1L);
    company.setName("Test Company");

    user = new User("admin@test.com", "Admin User");
    user.setId(1L);

    revenueKpi = new KPI(company, "REV", "Revenue", "$");
    revenueKpi.setId(1L);

    FiscalYear fiscalYear =
        new FiscalYear(company, LocalDate.of(2024, 7, 1), LocalDate.of(2025, 6, 30), "FY2024");
    fiscalYear.setId(1L);

    period = new Period();
    period.setId(1L);
    period.setFiscalYear(fiscalYear);
    period.setStartDate(LocalDate.of(2024, 7, 1));
    period.setEndDate(LocalDate.of(2024, 7, 31));
    period.setPeriodIndex(1);
  }

  @Test
  void importKPIValues_ValidCsv_ImportsAllValues() throws IOException {
    // Given
    String csv =
        """
            kpi_code,period_date,value,notes
            REV,2024-07-15,150000,Strong month
            REV,2024-08-15,165000,Peak sales
            """;

    Period period2 = new Period();
    period2.setId(2L);
    period2.setStartDate(LocalDate.of(2024, 8, 1));

    when(kpiRepository.findByCompanyAndCode(company, "REV")).thenReturn(Optional.of(revenueKpi));
    when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 15)))
        .thenReturn(Optional.of(period));
    when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 8, 15)))
        .thenReturn(Optional.of(period2));
    when(kpiValueRepository.findByKpiAndPeriod(any(), any())).thenReturn(Optional.empty());
    when(kpiValueRepository.save(any(KPIValue.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    ImportResult result = importService.importKPIValues(toInputStream(csv), company, user, false);

    // Then
    assertTrue(result.success());
    assertEquals(2, result.imported());
    assertEquals(0, result.updated());
    assertEquals(0, result.skipped());

    verify(kpiValueRepository, times(2)).save(any(KPIValue.class));
    verify(auditService)
        .logEvent(
            eq(company),
            eq(user),
            eq("KPI_VALUES_IMPORTED"),
            eq("KPIValue"),
            isNull(),
            contains("2 new"));
  }

  @Test
  void importKPIValues_MissingKpiCodeColumn_ReturnsError() throws IOException {
    // Given - CSV without required 'kpi_code' column
    String csv =
        """
            period_date,value
            2024-07-01,50000
            """;

    // When
    ImportResult result = importService.importKPIValues(toInputStream(csv), company, user, false);

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.toLowerCase().contains("kpi")));
    verify(kpiValueRepository, never()).save(any());
  }

  @Test
  void importKPIValues_MissingPeriodColumn_ReturnsError() throws IOException {
    // Given - CSV without required period column
    String csv =
        """
            kpi_code,value
            REV,50000
            """;

    // When
    ImportResult result = importService.importKPIValues(toInputStream(csv), company, user, false);

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("period")));
  }

  @Test
  void importKPIValues_MissingValueColumn_ReturnsError() throws IOException {
    // Given - CSV without required value column
    String csv =
        """
            kpi_code,period_date
            REV,2024-07-01
            """;

    // When
    ImportResult result = importService.importKPIValues(toInputStream(csv), company, user, false);

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("value")));
  }

  @Test
  void importKPIValues_KPINotFound_ReportsError() throws IOException {
    // Given
    String csv =
        """
            kpi_code,period_date,value
            UNKNOWN,2024-07-01,50000
            """;

    when(kpiRepository.findByCompanyAndCode(company, "UNKNOWN")).thenReturn(Optional.empty());

    // When
    ImportResult result = importService.importKPIValues(toInputStream(csv), company, user, false);

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("KPI 'UNKNOWN' not found")));
  }

  @Test
  void importKPIValues_PeriodNotFound_ReportsError() throws IOException {
    // Given
    String csv =
        """
            kpi_code,period_date,value
            REV,2030-01-01,50000
            """;

    when(kpiRepository.findByCompanyAndCode(company, "REV")).thenReturn(Optional.of(revenueKpi));
    when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2030, 1, 1)))
        .thenReturn(Optional.empty());

    // When
    ImportResult result = importService.importKPIValues(toInputStream(csv), company, user, false);

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("No period found")));
  }

  @Test
  void importKPIValues_InvalidDateFormat_ReportsError() throws IOException {
    // Given
    String csv =
        """
            kpi_code,period_date,value
            REV,July 2024,50000
            """;

    when(kpiRepository.findByCompanyAndCode(company, "REV")).thenReturn(Optional.of(revenueKpi));

    // When
    ImportResult result = importService.importKPIValues(toInputStream(csv), company, user, false);

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("Invalid date format")));
  }

  @Test
  void importKPIValues_InvalidValue_ReportsError() throws IOException {
    // Given
    String csv =
        """
            kpi_code,period_date,value
            REV,2024-07-01,abc
            """;

    when(kpiRepository.findByCompanyAndCode(company, "REV")).thenReturn(Optional.of(revenueKpi));
    when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 1)))
        .thenReturn(Optional.of(period));

    // When
    ImportResult result = importService.importKPIValues(toInputStream(csv), company, user, false);

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("Invalid value")));
  }

  @Test
  void importKPIValues_ExistingValue_SkipsWithoutUpdate() throws IOException {
    // Given
    String csv =
        """
            kpi_code,period_date,value
            REV,2024-07-01,50000
            """;

    KPIValue existing = new KPIValue();
    existing.setId(1L);
    existing.setKpi(revenueKpi);
    existing.setPeriod(period);
    existing.setValue(new BigDecimal("40000"));

    when(kpiRepository.findByCompanyAndCode(company, "REV")).thenReturn(Optional.of(revenueKpi));
    when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 1)))
        .thenReturn(Optional.of(period));
    when(kpiValueRepository.findByKpiAndPeriod(revenueKpi, period))
        .thenReturn(Optional.of(existing));

    // When
    ImportResult result =
        importService.importKPIValues(
            toInputStream(csv), company, user, false); // updateExisting = false

    // Then
    assertTrue(result.success());
    assertEquals(0, result.imported());
    assertEquals(0, result.updated());
    assertEquals(1, result.skipped());
    verify(kpiValueRepository, never()).save(any());
  }

  @Test
  void importKPIValues_ExistingValue_UpdatesWhenEnabled() throws IOException {
    // Given
    String csv =
        """
            kpi_code,period_date,value,notes
            REV,2024-07-01,50000,Updated notes
            """;

    KPIValue existing = new KPIValue();
    existing.setId(1L);
    existing.setKpi(revenueKpi);
    existing.setPeriod(period);
    existing.setValue(new BigDecimal("40000"));

    when(kpiRepository.findByCompanyAndCode(company, "REV")).thenReturn(Optional.of(revenueKpi));
    when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 1)))
        .thenReturn(Optional.of(period));
    when(kpiValueRepository.findByKpiAndPeriod(revenueKpi, period))
        .thenReturn(Optional.of(existing));
    when(kpiValueRepository.save(any(KPIValue.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    ImportResult result =
        importService.importKPIValues(
            toInputStream(csv), company, user, true); // updateExisting = true

    // Then
    assertTrue(result.success());
    assertEquals(0, result.imported());
    assertEquals(1, result.updated());
    assertEquals(0, result.skipped());

    ArgumentCaptor<KPIValue> valueCaptor = ArgumentCaptor.forClass(KPIValue.class);
    verify(kpiValueRepository).save(valueCaptor.capture());
    assertEquals(new BigDecimal("50000"), valueCaptor.getValue().getValue());
    assertEquals("Updated notes", valueCaptor.getValue().getNotes());
  }

  @Test
  void importKPIValues_MultipleDateFormats_ParsesCorrectly() throws IOException {
    // Given - Different date formats
    String csv =
        """
            kpi_code,period_date,value
            REV,01/07/2024,50000
            """;

    when(kpiRepository.findByCompanyAndCode(company, "REV")).thenReturn(Optional.of(revenueKpi));
    when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 1)))
        .thenReturn(Optional.of(period));
    when(kpiValueRepository.findByKpiAndPeriod(any(), any())).thenReturn(Optional.empty());
    when(kpiValueRepository.save(any(KPIValue.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    ImportResult result = importService.importKPIValues(toInputStream(csv), company, user, false);

    // Then
    assertTrue(result.success());
    assertEquals(1, result.imported());
  }

  @Test
  void importKPIValues_ValueWithCommasAndDollarSign_ParsesCorrectly() throws IOException {
    // Given
    String csv =
        """
            kpi_code,period_date,value
            REV,2024-07-01,"$150,000"
            """;

    when(kpiRepository.findByCompanyAndCode(company, "REV")).thenReturn(Optional.of(revenueKpi));
    when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 1)))
        .thenReturn(Optional.of(period));
    when(kpiValueRepository.findByKpiAndPeriod(any(), any())).thenReturn(Optional.empty());
    when(kpiValueRepository.save(any(KPIValue.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    ImportResult result = importService.importKPIValues(toInputStream(csv), company, user, false);

    // Then
    assertTrue(result.success());
    assertEquals(1, result.imported());

    ArgumentCaptor<KPIValue> valueCaptor = ArgumentCaptor.forClass(KPIValue.class);
    verify(kpiValueRepository).save(valueCaptor.capture());
    assertEquals(new BigDecimal("150000"), valueCaptor.getValue().getValue());
  }

  @Test
  void importKPIValues_FlexibleColumnNames_ParsesCorrectly() throws IOException {
    // Given - Using alternative column names
    String csv =
        """
            kpi,date,value,note
            REV,2024-07-01,50000,Test note
            """;

    when(kpiRepository.findByCompanyAndCode(company, "REV")).thenReturn(Optional.of(revenueKpi));
    when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 1)))
        .thenReturn(Optional.of(period));
    when(kpiValueRepository.findByKpiAndPeriod(any(), any())).thenReturn(Optional.empty());
    when(kpiValueRepository.save(any(KPIValue.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    ImportResult result = importService.importKPIValues(toInputStream(csv), company, user, false);

    // Then
    assertTrue(result.success());
    assertEquals(1, result.imported());

    ArgumentCaptor<KPIValue> valueCaptor = ArgumentCaptor.forClass(KPIValue.class);
    verify(kpiValueRepository).save(valueCaptor.capture());
    assertEquals("Test note", valueCaptor.getValue().getNotes());
  }

  @Test
  void previewImport_DoesNotSaveAnything() throws IOException {
    // Given
    String csv =
        """
            kpi_code,period_date,value
            REV,2024-07-01,50000
            """;

    when(kpiRepository.findByCompanyAndCode(company, "REV")).thenReturn(Optional.of(revenueKpi));
    when(periodRepository.findByCompanyAndDate(company, LocalDate.of(2024, 7, 1)))
        .thenReturn(Optional.of(period));
    when(kpiValueRepository.findByKpiAndPeriod(any(), any())).thenReturn(Optional.empty());

    // When
    ImportResult result = importService.previewImport(toInputStream(csv), company, false);

    // Then
    assertTrue(result.success());
    assertEquals(1, result.imported());

    // Preview should NOT save anything
    verify(kpiValueRepository, never()).save(any());
    verify(auditService, never()).logEvent(any(), any(), any(), any(), any(), any());
  }

  @Test
  void getSampleCsvContent_ReturnsValidFormat() {
    // When
    String sample = importService.getSampleCsvContent();

    // Then
    assertNotNull(sample);
    assertTrue(sample.contains("kpi_code"));
    assertTrue(sample.contains("period_date"));
    assertTrue(sample.contains("value"));
    assertTrue(sample.contains("notes"));
  }

  @Test
  void importKPIValues_EmptyFile_ReturnsError() throws IOException {
    // Given
    String csv = "";

    // When
    ImportResult result = importService.importKPIValues(toInputStream(csv), company, user, false);

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("empty")));
  }

  private InputStream toInputStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
