package com.example.application.service;

import com.example.application.domain.Account;
import com.example.application.domain.Company;
import com.example.application.domain.Product;
import com.example.application.domain.User;
import com.example.application.repository.AccountRepository;
import com.example.application.repository.ProductRepository;
import com.example.application.service.ProductImportService.ImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProductImportService CSV import functionality.
 */
@ExtendWith(MockitoExtension.class)
class ProductImportServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AuditService auditService;

    private ProductImportService importService;

    private Company company;
    private User user;

    @BeforeEach
    void setUp() {
        importService = new ProductImportService(productRepository, accountRepository, auditService);

        company = new Company();
        company.setId(1L);
        company.setName("Test Company");

        user = new User("admin@test.com", "Admin User");
        user.setId(1L);
    }

    @Test
    void importProducts_ValidCsv_ImportsAllProducts() throws IOException {
        // Given
        String csv = """
            code,name,category,sellPrice,buyPrice
            PROD001,Widget A,Widgets,25.00,10.50
            PROD002,Widget B,Widgets,35.00,15.00
            """;

        when(productRepository.findByCompanyAndCode(any(), anyString()))
            .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importProducts(
            toInputStream(csv), company, user, false);

        // Then
        assertTrue(result.success());
        assertEquals(2, result.imported());
        assertEquals(0, result.updated());
        assertEquals(0, result.skipped());

        verify(productRepository, times(2)).save(any(Product.class));
        verify(auditService).logEvent(eq(company), eq(user), eq("PRODUCTS_IMPORTED"),
            eq("Product"), isNull(), contains("2 new"));
    }

    @Test
    void importProducts_MissingCodeColumn_ReturnsError() throws IOException {
        // Given - CSV without required 'code' column
        String csv = """
            name,category,sellPrice
            Widget A,Widgets,25.00
            """;

        // When
        ImportResult result = importService.importProducts(
            toInputStream(csv), company, user, false);

        // Then
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("code")));
        verify(productRepository, never()).save(any());
    }

    @Test
    void importProducts_MissingNameColumn_ReturnsError() throws IOException {
        // Given - CSV without required 'name' column
        String csv = """
            code,category,sellPrice
            PROD001,Widgets,25.00
            """;

        // When
        ImportResult result = importService.importProducts(
            toInputStream(csv), company, user, false);

        // Then
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("name")));
        verify(productRepository, never()).save(any());
    }

    @Test
    void importProducts_EmptyFile_ReturnsError() throws IOException {
        // Given
        String csv = "";

        // When
        ImportResult result = importService.importProducts(
            toInputStream(csv), company, user, false);

        // Then
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("empty")));
    }

    @Test
    void importProducts_CodeTooLong_ReportsError() throws IOException {
        // Given - Code exceeds 31 characters
        String csv = """
            code,name
            VERYLONGPRODUCTCODE1234567890123,Widget A
            """;

        // When
        ImportResult result = importService.importProducts(
            toInputStream(csv), company, user, false);

        // Then
        assertFalse(result.success());
        assertEquals(0, result.imported());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("exceeds maximum length")));
    }

    @Test
    void importProducts_ExistingProduct_SkipsWithoutUpdate() throws IOException {
        // Given
        String csv = """
            code,name,sellPrice
            PROD001,Widget A,99.00
            """;

        Product existing = new Product(company, "PROD001", "Old Widget");
        existing.setSellPrice(new BigDecimal("25.00"));
        when(productRepository.findByCompanyAndCode(company, "PROD001"))
            .thenReturn(Optional.of(existing));

        // When
        ImportResult result = importService.importProducts(
            toInputStream(csv), company, user, false); // updateExisting = false

        // Then
        assertTrue(result.success());
        assertEquals(0, result.imported());
        assertEquals(0, result.updated());
        assertEquals(1, result.skipped());
        verify(productRepository, never()).save(any());
    }

    @Test
    void importProducts_ExistingProduct_UpdatesWhenEnabled() throws IOException {
        // Given
        String csv = """
            code,name,sellPrice
            PROD001,Widget A,99.00
            """;

        Product existing = new Product(company, "PROD001", "Old Widget");
        existing.setSellPrice(new BigDecimal("25.00"));
        when(productRepository.findByCompanyAndCode(company, "PROD001"))
            .thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importProducts(
            toInputStream(csv), company, user, true); // updateExisting = true

        // Then
        assertTrue(result.success());
        assertEquals(0, result.imported());
        assertEquals(1, result.updated());
        assertEquals(0, result.skipped());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertEquals("Widget A", productCaptor.getValue().getName());
        assertEquals(new BigDecimal("99.00"), productCaptor.getValue().getSellPrice());
    }

    @Test
    void importProducts_AllFields_ParsesCorrectly() throws IOException {
        // Given
        String csv = """
            code,name,description,category,buyPrice,sellPrice,taxCode,barcode,stickyNote
            PROD001,Widget A,Standard widget,Widgets,10.50,25.00,GST,1234567890123,Check stock
            """;

        when(productRepository.findByCompanyAndCode(any(), anyString()))
            .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importProducts(
            toInputStream(csv), company, user, false);

        // Then
        assertTrue(result.success());
        assertEquals(1, result.imported());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product saved = productCaptor.getValue();

        assertEquals("PROD001", saved.getCode());
        assertEquals("Widget A", saved.getName());
        assertEquals("Standard widget", saved.getDescription());
        assertEquals("Widgets", saved.getCategory());
        assertEquals(new BigDecimal("10.50"), saved.getBuyPrice());
        assertEquals(new BigDecimal("25.00"), saved.getSellPrice());
        assertEquals("GST", saved.getTaxCode());
        assertEquals("1234567890123", saved.getBarcode());
        assertEquals("Check stock", saved.getStickyNote());
    }

    @Test
    void importProducts_PriceWithCurrencySymbol_ParsesCorrectly() throws IOException {
        // Given - Prices with $ and commas
        String csv = """
            code,name,sellPrice,buyPrice
            PROD001,Widget A,"$1,234.56","$567.89"
            """;

        when(productRepository.findByCompanyAndCode(any(), anyString()))
            .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importProducts(
            toInputStream(csv), company, user, false);

        // Then
        assertTrue(result.success());
        assertEquals(1, result.imported());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertEquals(new BigDecimal("1234.56"), productCaptor.getValue().getSellPrice());
        assertEquals(new BigDecimal("567.89"), productCaptor.getValue().getBuyPrice());
    }

    @Test
    void importProducts_QuotedFieldsWithCommas_ParsesCorrectly() throws IOException {
        // Given - CSV with commas inside quoted fields
        String csv = """
            code,name,description
            PROD001,"Widget A, Deluxe","Standard widget, blue color"
            """;

        when(productRepository.findByCompanyAndCode(any(), anyString()))
            .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importProducts(
            toInputStream(csv), company, user, false);

        // Then
        assertTrue(result.success());
        assertEquals(1, result.imported());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertEquals("Widget A, Deluxe", productCaptor.getValue().getName());
        assertEquals("Standard widget, blue color", productCaptor.getValue().getDescription());
    }

    @Test
    void importProducts_FlexibleColumnNames_ParsesCorrectly() throws IOException {
        // Given - CSV with different column name formats
        String csv = """
            Code,NAME,sell_price,buy-price,Tax Code
            PROD001,Widget A,25.00,10.50,GST
            """;

        when(productRepository.findByCompanyAndCode(any(), anyString()))
            .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importProducts(
            toInputStream(csv), company, user, false);

        // Then
        assertTrue(result.success());
        assertEquals(1, result.imported());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertEquals("PROD001", productCaptor.getValue().getCode());
        assertEquals("Widget A", productCaptor.getValue().getName());
        assertEquals(new BigDecimal("25.00"), productCaptor.getValue().getSellPrice());
        assertEquals(new BigDecimal("10.50"), productCaptor.getValue().getBuyPrice());
        assertEquals("GST", productCaptor.getValue().getTaxCode());
    }

    @Test
    void importProducts_WithAccountCodes_LinksAccounts() throws IOException {
        // Given
        String csv = """
            code,name,salesAccountCode,purchaseAccountCode
            PROD001,Widget A,4100,5100
            """;

        Account salesAccount = new Account();
        salesAccount.setId(1L);
        salesAccount.setCode("4100");
        salesAccount.setName("Sales");

        Account purchaseAccount = new Account();
        purchaseAccount.setId(2L);
        purchaseAccount.setCode("5100");
        purchaseAccount.setName("Purchases");

        when(productRepository.findByCompanyAndCode(any(), anyString()))
            .thenReturn(Optional.empty());
        when(accountRepository.findByCompanyAndCode(company, "4100"))
            .thenReturn(Optional.of(salesAccount));
        when(accountRepository.findByCompanyAndCode(company, "5100"))
            .thenReturn(Optional.of(purchaseAccount));
        when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ImportResult result = importService.importProducts(
            toInputStream(csv), company, user, false);

        // Then
        assertTrue(result.success());
        assertEquals(1, result.imported());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertEquals(salesAccount, productCaptor.getValue().getSalesAccount());
        assertEquals(purchaseAccount, productCaptor.getValue().getPurchaseAccount());
    }

    @Test
    void previewImport_DoesNotSaveAnything() throws IOException {
        // Given
        String csv = """
            code,name
            PROD001,Widget A
            """;

        when(productRepository.findByCompanyAndCode(any(), anyString()))
            .thenReturn(Optional.empty());

        // When
        ImportResult result = importService.previewImport(
            toInputStream(csv), company, false);

        // Then
        assertTrue(result.success());
        assertEquals(1, result.imported());

        // Preview should NOT save anything
        verify(productRepository, never()).save(any());
        verify(auditService, never()).logEvent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void getSampleCsvContent_ReturnsValidFormat() {
        // When
        String sample = importService.getSampleCsvContent();

        // Then
        assertNotNull(sample);
        assertTrue(sample.contains("code,name"));
        assertTrue(sample.contains("PROD001"));
        assertTrue(sample.contains("sellPrice"));
        assertTrue(sample.contains("buyPrice"));
    }

    private InputStream toInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
