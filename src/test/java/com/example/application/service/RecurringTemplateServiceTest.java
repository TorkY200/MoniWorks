package com.example.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.domain.*;
import com.example.application.domain.RecurringTemplate.*;
import com.example.application.repository.RecurrenceExecutionLogRepository;
import com.example.application.repository.RecurringTemplateRepository;

/**
 * Tests for RecurringTemplateService, particularly the price/description update feature for
 * recurring invoice and bill templates.
 */
@ExtendWith(MockitoExtension.class)
class RecurringTemplateServiceTest {

  @Mock private RecurringTemplateRepository templateRepository;
  @Mock private RecurrenceExecutionLogRepository logRepository;
  @Mock private TransactionService transactionService;
  @Mock private PostingService postingService;
  @Mock private SalesInvoiceService salesInvoiceService;
  @Mock private SupplierBillService supplierBillService;
  @Mock private AccountService accountService;
  @Mock private ProductService productService;
  @Mock private AuditService auditService;

  private RecurringTemplateService service;
  private Company company;
  private Contact contact;
  private Account incomeAccount;
  private Product product;

  @BeforeEach
  void setUp() {
    service =
        new RecurringTemplateService(
            templateRepository,
            logRepository,
            transactionService,
            postingService,
            salesInvoiceService,
            supplierBillService,
            accountService,
            productService,
            auditService);

    // Set up common test fixtures
    company = new Company();
    company.setId(1L);
    company.setName("Test Company");

    contact = new Contact(company, "CUST001", "Test Customer", Contact.ContactType.CUSTOMER);
    contact.setId(10L);

    incomeAccount = new Account(company, "4000", "Sales Revenue", Account.AccountType.INCOME);
    incomeAccount.setId(100L);

    product = new Product(company, "PROD001", "Test Product");
    product.setId(200L);
    product.setSellPrice(new BigDecimal("150.00")); // Current price
    product.setBuyPrice(new BigDecimal("80.00")); // Current buy price
    product.setTaxCode("GST");
  }

  @Test
  void executeInvoiceTemplate_withUpdatePricesDisabled_usesStoredPrice() {
    // Given a template with frozen prices
    String payloadJson =
        """
        {
          "dueDays": 14,
          "lines": [
            {
              "accountId": 100,
              "description": "Old Product Name",
              "quantity": "2",
              "unitPrice": "100.00",
              "taxCode": "GST",
              "productId": 200
            }
          ]
        }
        """;

    RecurringTemplate template =
        new RecurringTemplate(
            company,
            "Monthly Invoice",
            TemplateType.INVOICE,
            payloadJson,
            Frequency.MONTHLY,
            LocalDate.now());
    template.setId(1L);
    template.setContact(contact);
    template.setUpdatePricesOnExecution(false); // Disabled - use frozen prices

    SalesInvoice mockInvoice = new SalesInvoice();
    mockInvoice.setId(999L);

    when(accountService.findById(100L)).thenReturn(Optional.of(incomeAccount));
    when(salesInvoiceService.createInvoice(any(), any(), any(), any(), any()))
        .thenReturn(mockInvoice);
    when(templateRepository.save(any())).thenReturn(template);

    // When
    RecurrenceExecutionLog result = service.executeNow(template, null);

    // Then - should use the stored price (100.00), not the current product price (150.00)
    assertTrue(result.isSuccess());

    ArgumentCaptor<BigDecimal> priceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
    ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);

    verify(salesInvoiceService)
        .addLine(
            eq(mockInvoice),
            eq(incomeAccount),
            descCaptor.capture(),
            any(BigDecimal.class),
            priceCaptor.capture(),
            anyString());

    // Price should be the stored value (100.00), not current product price (150.00)
    assertEquals(0, new BigDecimal("100.00").compareTo(priceCaptor.getValue()));
    // Description should be the stored value
    assertEquals("Old Product Name", descCaptor.getValue());
  }

  @Test
  void executeInvoiceTemplate_withUpdatePricesEnabled_fetchesCurrentProductPrice() {
    // Given a template with update prices enabled
    String payloadJson =
        """
        {
          "dueDays": 14,
          "lines": [
            {
              "accountId": 100,
              "description": "Old Product Name",
              "quantity": "2",
              "unitPrice": "100.00",
              "taxCode": "OLD_TAX",
              "productId": 200
            }
          ]
        }
        """;

    RecurringTemplate template =
        new RecurringTemplate(
            company,
            "Monthly Invoice",
            TemplateType.INVOICE,
            payloadJson,
            Frequency.MONTHLY,
            LocalDate.now());
    template.setId(1L);
    template.setContact(contact);
    template.setUpdatePricesOnExecution(true); // Enabled - fetch current prices

    SalesInvoice mockInvoice = new SalesInvoice();
    mockInvoice.setId(999L);

    when(accountService.findById(100L)).thenReturn(Optional.of(incomeAccount));
    when(productService.findById(200L)).thenReturn(Optional.of(product));
    when(salesInvoiceService.createInvoice(any(), any(), any(), any(), any()))
        .thenReturn(mockInvoice);
    when(templateRepository.save(any())).thenReturn(template);

    // When
    RecurrenceExecutionLog result = service.executeNow(template, null);

    // Then - should use current product sell price (150.00) and name
    assertTrue(result.isSuccess());

    ArgumentCaptor<BigDecimal> priceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
    ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> taxCaptor = ArgumentCaptor.forClass(String.class);

    verify(salesInvoiceService)
        .addLine(
            eq(mockInvoice),
            eq(incomeAccount),
            descCaptor.capture(),
            any(BigDecimal.class),
            priceCaptor.capture(),
            taxCaptor.capture());

    // Price should be current product sell price (150.00)
    assertEquals(0, new BigDecimal("150.00").compareTo(priceCaptor.getValue()));
    // Description should be current product name
    assertEquals("Test Product", descCaptor.getValue());
    // Tax code should be updated from product
    assertEquals("GST", taxCaptor.getValue());
  }

  @Test
  void executeInvoiceTemplate_withUpdatePricesEnabled_productNotFound_usesStoredValues() {
    // Given a template with update prices enabled but product has been deleted
    String payloadJson =
        """
        {
          "dueDays": 14,
          "lines": [
            {
              "accountId": 100,
              "description": "Old Product Name",
              "quantity": "2",
              "unitPrice": "100.00",
              "taxCode": "GST",
              "productId": 999
            }
          ]
        }
        """;

    RecurringTemplate template =
        new RecurringTemplate(
            company,
            "Monthly Invoice",
            TemplateType.INVOICE,
            payloadJson,
            Frequency.MONTHLY,
            LocalDate.now());
    template.setId(1L);
    template.setContact(contact);
    template.setUpdatePricesOnExecution(true); // Enabled but product doesn't exist

    SalesInvoice mockInvoice = new SalesInvoice();
    mockInvoice.setId(999L);

    when(accountService.findById(100L)).thenReturn(Optional.of(incomeAccount));
    when(productService.findById(999L)).thenReturn(Optional.empty()); // Product not found
    when(salesInvoiceService.createInvoice(any(), any(), any(), any(), any()))
        .thenReturn(mockInvoice);
    when(templateRepository.save(any())).thenReturn(template);

    // When
    RecurrenceExecutionLog result = service.executeNow(template, null);

    // Then - should fall back to stored values
    assertTrue(result.isSuccess());

    ArgumentCaptor<BigDecimal> priceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
    ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);

    verify(salesInvoiceService)
        .addLine(
            eq(mockInvoice),
            eq(incomeAccount),
            descCaptor.capture(),
            any(BigDecimal.class),
            priceCaptor.capture(),
            anyString());

    // Should use stored values since product not found
    assertEquals(0, new BigDecimal("100.00").compareTo(priceCaptor.getValue()));
    assertEquals("Old Product Name", descCaptor.getValue());
  }

  @Test
  void executeInvoiceTemplate_withUpdatePricesEnabled_noProductId_usesStoredValues() {
    // Given a template line without product reference (free-form line)
    String payloadJson =
        """
        {
          "dueDays": 14,
          "lines": [
            {
              "accountId": 100,
              "description": "Service Fee",
              "quantity": "1",
              "unitPrice": "250.00",
              "taxCode": "GST"
            }
          ]
        }
        """;

    RecurringTemplate template =
        new RecurringTemplate(
            company,
            "Monthly Invoice",
            TemplateType.INVOICE,
            payloadJson,
            Frequency.MONTHLY,
            LocalDate.now());
    template.setId(1L);
    template.setContact(contact);
    template.setUpdatePricesOnExecution(true); // Enabled but no productId in payload

    SalesInvoice mockInvoice = new SalesInvoice();
    mockInvoice.setId(999L);

    when(accountService.findById(100L)).thenReturn(Optional.of(incomeAccount));
    when(salesInvoiceService.createInvoice(any(), any(), any(), any(), any()))
        .thenReturn(mockInvoice);
    when(templateRepository.save(any())).thenReturn(template);

    // When
    RecurrenceExecutionLog result = service.executeNow(template, null);

    // Then - should use stored values (no product to look up)
    assertTrue(result.isSuccess());

    ArgumentCaptor<BigDecimal> priceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
    ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);

    verify(salesInvoiceService)
        .addLine(
            eq(mockInvoice),
            eq(incomeAccount),
            descCaptor.capture(),
            any(BigDecimal.class),
            priceCaptor.capture(),
            anyString());

    assertEquals(0, new BigDecimal("250.00").compareTo(priceCaptor.getValue()));
    assertEquals("Service Fee", descCaptor.getValue());

    // ProductService should never be called since no productId in payload
    verify(productService, never()).findById(anyLong());
  }

  @Test
  void executeBillTemplate_withUpdatePricesEnabled_fetchesCurrentProductBuyPrice() {
    // Given a bill template with update prices enabled
    String payloadJson =
        """
        {
          "dueDays": 30,
          "lines": [
            {
              "accountId": 100,
              "description": "Old Product Name",
              "quantity": "5",
              "unitPrice": "50.00",
              "taxCode": "OLD_TAX",
              "productId": 200
            }
          ]
        }
        """;

    RecurringTemplate template =
        new RecurringTemplate(
            company,
            "Monthly Bill",
            TemplateType.BILL,
            payloadJson,
            Frequency.MONTHLY,
            LocalDate.now());
    template.setId(1L);
    template.setContact(contact);
    template.setUpdatePricesOnExecution(true); // Enabled - fetch current prices

    SupplierBill mockBill = new SupplierBill();
    mockBill.setId(888L);

    when(accountService.findById(100L)).thenReturn(Optional.of(incomeAccount));
    when(productService.findById(200L)).thenReturn(Optional.of(product));
    when(supplierBillService.createBill(any(), any(), any(), any(), any())).thenReturn(mockBill);
    when(templateRepository.save(any())).thenReturn(template);

    // When
    RecurrenceExecutionLog result = service.executeNow(template, null);

    // Then - should use current product BUY price (80.00), not sell price
    assertTrue(result.isSuccess());

    ArgumentCaptor<BigDecimal> priceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
    ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> taxCaptor = ArgumentCaptor.forClass(String.class);

    verify(supplierBillService)
        .addLine(
            eq(mockBill),
            eq(incomeAccount),
            descCaptor.capture(),
            any(BigDecimal.class),
            priceCaptor.capture(),
            taxCaptor.capture());

    // Price should be current product BUY price (80.00)
    assertEquals(0, new BigDecimal("80.00").compareTo(priceCaptor.getValue()));
    // Description should be current product name
    assertEquals("Test Product", descCaptor.getValue());
    // Tax code should be updated from product
    assertEquals("GST", taxCaptor.getValue());
  }

  @Test
  void executeBillTemplate_withUpdatePricesDisabled_usesStoredPrice() {
    // Given a bill template with update prices disabled
    String payloadJson =
        """
        {
          "dueDays": 30,
          "lines": [
            {
              "accountId": 100,
              "description": "Old Product Name",
              "quantity": "5",
              "unitPrice": "50.00",
              "taxCode": "GST",
              "productId": 200
            }
          ]
        }
        """;

    RecurringTemplate template =
        new RecurringTemplate(
            company,
            "Monthly Bill",
            TemplateType.BILL,
            payloadJson,
            Frequency.MONTHLY,
            LocalDate.now());
    template.setId(1L);
    template.setContact(contact);
    template.setUpdatePricesOnExecution(false); // Disabled

    SupplierBill mockBill = new SupplierBill();
    mockBill.setId(888L);

    when(accountService.findById(100L)).thenReturn(Optional.of(incomeAccount));
    when(supplierBillService.createBill(any(), any(), any(), any(), any())).thenReturn(mockBill);
    when(templateRepository.save(any())).thenReturn(template);

    // When
    RecurrenceExecutionLog result = service.executeNow(template, null);

    // Then - should use stored price (50.00)
    assertTrue(result.isSuccess());

    ArgumentCaptor<BigDecimal> priceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
    ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);

    verify(supplierBillService)
        .addLine(
            eq(mockBill),
            eq(incomeAccount),
            descCaptor.capture(),
            any(BigDecimal.class),
            priceCaptor.capture(),
            anyString());

    // Price should be stored value (50.00), not current buy price (80.00)
    assertEquals(0, new BigDecimal("50.00").compareTo(priceCaptor.getValue()));
    assertEquals("Old Product Name", descCaptor.getValue());

    // ProductService should never be called when update prices is disabled
    verify(productService, never()).findById(anyLong());
  }

  @Test
  void updatePricesOnExecution_defaultsToFalse() {
    // Given a new template
    RecurringTemplate template =
        new RecurringTemplate(
            company,
            "Test Template",
            TemplateType.INVOICE,
            "{}",
            Frequency.MONTHLY,
            LocalDate.now());

    // Then - update prices should default to false
    assertFalse(template.isUpdatePricesOnExecution());
  }

  @Test
  void updatePricesOnExecution_canBeSetAndRetrieved() {
    // Given a template
    RecurringTemplate template =
        new RecurringTemplate(
            company,
            "Test Template",
            TemplateType.INVOICE,
            "{}",
            Frequency.MONTHLY,
            LocalDate.now());

    // When
    template.setUpdatePricesOnExecution(true);

    // Then
    assertTrue(template.isUpdatePricesOnExecution());

    // When
    template.setUpdatePricesOnExecution(false);

    // Then
    assertFalse(template.isUpdatePricesOnExecution());
  }
}
