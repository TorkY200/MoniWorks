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
import com.example.application.domain.Account.AccountType;
import com.example.application.domain.Transaction.TransactionType;
import com.example.application.domain.TransactionLine.Direction;
import com.example.application.repository.AccountRepository;
import com.example.application.repository.DepartmentRepository;
import com.example.application.repository.TransactionRepository;
import com.example.application.service.TransactionImportService.ImportConfig;
import com.example.application.service.TransactionImportService.ImportResult;

/** Unit tests for TransactionImportService CSV import functionality. */
@ExtendWith(MockitoExtension.class)
class TransactionImportServiceTest {

  @Mock private TransactionRepository transactionRepository;

  @Mock private AccountRepository accountRepository;

  @Mock private DepartmentRepository departmentRepository;

  @Mock private PostingService postingService;

  @Mock private AuditService auditService;

  private TransactionImportService importService;

  private Company company;
  private User user;
  private Account bankAccount;
  private Account expenseAccount;
  private Account incomeAccount;
  private Department salesDept;

  @BeforeEach
  void setUp() {
    importService =
        new TransactionImportService(
            transactionRepository,
            accountRepository,
            departmentRepository,
            postingService,
            auditService);

    company = new Company();
    company.setId(1L);
    company.setName("Test Company");

    user = new User("admin@test.com", "Admin User");
    user.setId(1L);

    bankAccount = new Account(company, "1000", "Bank Account", AccountType.ASSET);
    bankAccount.setId(1L);

    expenseAccount = new Account(company, "5000", "Office Expenses", AccountType.EXPENSE);
    expenseAccount.setId(2L);

    incomeAccount = new Account(company, "4000", "Sales Income", AccountType.INCOME);
    incomeAccount.setId(3L);

    salesDept = new Department(company, "SALES", "Sales Department");
    salesDept.setId(1L);
  }

  @Test
  void importTransactions_ValidCsv_ImportsBalancedTransaction() throws IOException {
    // Given - a balanced payment transaction with two lines
    String csv =
        """
            date,type,description,reference,account_code,amount,direction
            2024-01-15,PAYMENT,Office Supplies,REF001,5000,100.00,DEBIT
            2024-01-15,PAYMENT,Office Supplies,REF001,1000,100.00,CREDIT
            """;

    when(accountRepository.findByCompanyAndCode(company, "5000"))
        .thenReturn(Optional.of(expenseAccount));
    when(accountRepository.findByCompanyAndCode(company, "1000"))
        .thenReturn(Optional.of(bankAccount));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            invocation -> {
              Transaction t = invocation.getArgument(0);
              t.setId(1L);
              return t;
            });

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertTrue(result.success());
    assertEquals(1, result.imported()); // One transaction with two lines
    assertEquals(0, result.skipped());

    ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).save(txCaptor.capture());
    Transaction saved = txCaptor.getValue();

    assertEquals(TransactionType.PAYMENT, saved.getType());
    assertEquals(LocalDate.of(2024, 1, 15), saved.getTransactionDate());
    assertEquals("Office Supplies", saved.getDescription());
    assertEquals("REF001", saved.getReference());
    assertEquals(2, saved.getLines().size());

    verify(auditService)
        .logEvent(
            eq(company),
            eq(user),
            eq("TRANSACTIONS_IMPORTED"),
            eq("Transaction"),
            isNull(),
            contains("1 transactions"));
  }

  @Test
  void importTransactions_MissingDateColumn_ReturnsError() throws IOException {
    // Given - CSV without required 'date' column
    String csv =
        """
            type,description,account_code,amount,direction
            PAYMENT,Office Supplies,5000,100.00,DEBIT
            """;

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("date")));
    verify(transactionRepository, never()).save(any());
  }

  @Test
  void importTransactions_MissingTypeColumn_ReturnsError() throws IOException {
    // Given - CSV without required 'type' column
    String csv =
        """
            date,description,account_code,amount,direction
            2024-01-15,Office Supplies,5000,100.00,DEBIT
            """;

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("type")));
    verify(transactionRepository, never()).save(any());
  }

  @Test
  void importTransactions_EmptyFile_ReturnsError() throws IOException {
    // Given
    String csv = "";

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("empty")));
  }

  @Test
  void importTransactions_UnbalancedTransaction_ReturnsError() throws IOException {
    // Given - transaction where debits don't equal credits
    String csv =
        """
            date,type,description,reference,account_code,amount,direction
            2024-01-15,PAYMENT,Office Supplies,REF001,5000,100.00,DEBIT
            2024-01-15,PAYMENT,Office Supplies,REF001,1000,50.00,CREDIT
            """;

    when(accountRepository.findByCompanyAndCode(company, "5000"))
        .thenReturn(Optional.of(expenseAccount));
    when(accountRepository.findByCompanyAndCode(company, "1000"))
        .thenReturn(Optional.of(bankAccount));

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("unbalanced")));
    verify(transactionRepository, never()).save(any());
  }

  @Test
  void importTransactions_AccountNotFound_ReturnsError() throws IOException {
    // Given - CSV with non-existent account code
    String csv =
        """
            date,type,description,account_code,amount,direction
            2024-01-15,PAYMENT,Office Supplies,9999,100.00,DEBIT
            """;

    when(accountRepository.findByCompanyAndCode(company, "9999")).thenReturn(Optional.empty());

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("not found")));
  }

  @Test
  void importTransactions_InvalidDateFormat_ReturnsError() throws IOException {
    // Given - CSV with invalid date
    String csv =
        """
            date,type,description,account_code,amount,direction
            not-a-date,PAYMENT,Office Supplies,5000,100.00,DEBIT
            """;

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("Invalid date")));
  }

  @Test
  void importTransactions_InvalidTransactionType_ReturnsError() throws IOException {
    // Given - CSV with invalid transaction type
    String csv =
        """
            date,type,description,account_code,amount,direction
            2024-01-15,INVALID,Office Supplies,5000,100.00,DEBIT
            """;

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("Invalid transaction type")));
  }

  @Test
  void importTransactions_InvalidAmount_ReturnsError() throws IOException {
    // Given - CSV with invalid amount
    String csv =
        """
            date,type,description,account_code,amount,direction
            2024-01-15,PAYMENT,Office Supplies,5000,not-a-number,DEBIT
            """;

    when(accountRepository.findByCompanyAndCode(company, "5000"))
        .thenReturn(Optional.of(expenseAccount));

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("Invalid amount")));
  }

  @Test
  void importTransactions_InvalidDirection_ReturnsError() throws IOException {
    // Given - CSV with invalid direction
    String csv =
        """
            date,type,description,account_code,amount,direction
            2024-01-15,PAYMENT,Office Supplies,5000,100.00,INVALID
            """;

    when(accountRepository.findByCompanyAndCode(company, "5000"))
        .thenReturn(Optional.of(expenseAccount));

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("Invalid direction")));
  }

  @Test
  void importTransactions_WithTaxCodeAndMemo_ParsesCorrectly() throws IOException {
    // Given
    String csv =
        """
            date,type,description,reference,account_code,amount,direction,tax_code,memo
            2024-01-15,PAYMENT,Office Supplies,REF001,5000,100.00,DEBIT,S15,Paper and pens
            2024-01-15,PAYMENT,Office Supplies,REF001,1000,100.00,CREDIT,,Bank payment
            """;

    when(accountRepository.findByCompanyAndCode(company, "5000"))
        .thenReturn(Optional.of(expenseAccount));
    when(accountRepository.findByCompanyAndCode(company, "1000"))
        .thenReturn(Optional.of(bankAccount));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            invocation -> {
              Transaction t = invocation.getArgument(0);
              t.setId(1L);
              return t;
            });

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertTrue(result.success());

    ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).save(txCaptor.capture());
    Transaction saved = txCaptor.getValue();

    assertEquals(2, saved.getLines().size());
    TransactionLine firstLine = saved.getLines().get(0);
    assertEquals("S15", firstLine.getTaxCode());
    assertEquals("Paper and pens", firstLine.getMemo());
  }

  @Test
  void importTransactions_WithDepartment_ParsesCorrectly() throws IOException {
    // Given
    String csv =
        """
            date,type,description,reference,account_code,amount,direction,department_code
            2024-01-15,RECEIPT,Consulting,REF002,4000,500.00,CREDIT,SALES
            2024-01-15,RECEIPT,Consulting,REF002,1000,500.00,DEBIT,SALES
            """;

    when(accountRepository.findByCompanyAndCode(company, "4000"))
        .thenReturn(Optional.of(incomeAccount));
    when(accountRepository.findByCompanyAndCode(company, "1000"))
        .thenReturn(Optional.of(bankAccount));
    when(departmentRepository.findByCompanyAndCode(company, "SALES"))
        .thenReturn(Optional.of(salesDept));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            invocation -> {
              Transaction t = invocation.getArgument(0);
              t.setId(1L);
              return t;
            });

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertTrue(result.success());

    ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).save(txCaptor.capture());
    Transaction saved = txCaptor.getValue();

    assertEquals(TransactionType.RECEIPT, saved.getType());
    assertEquals(2, saved.getLines().size());
    assertEquals(salesDept, saved.getLines().get(0).getDepartment());
  }

  @Test
  void importTransactions_DepartmentNotFound_ReturnsError() throws IOException {
    // Given
    String csv =
        """
            date,type,description,account_code,amount,direction,department_code
            2024-01-15,PAYMENT,Office Supplies,5000,100.00,DEBIT,NONEXISTENT
            """;

    when(accountRepository.findByCompanyAndCode(company, "5000"))
        .thenReturn(Optional.of(expenseAccount));
    when(departmentRepository.findByCompanyAndCode(company, "NONEXISTENT"))
        .thenReturn(Optional.empty());

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("Department")));
  }

  @Test
  void importTransactions_MultipleDateFormats_ParsesCorrectly() throws IOException {
    // Given - CSV with different date formats
    String csv =
        """
            date,type,description,account_code,amount,direction
            2024-01-15,JOURNAL,Entry 1,5000,100.00,DEBIT
            2024-01-15,JOURNAL,Entry 1,1000,100.00,CREDIT
            15/01/2024,JOURNAL,Entry 2,5000,200.00,DEBIT
            15/01/2024,JOURNAL,Entry 2,1000,200.00,CREDIT
            """;

    when(accountRepository.findByCompanyAndCode(company, "5000"))
        .thenReturn(Optional.of(expenseAccount));
    when(accountRepository.findByCompanyAndCode(company, "1000"))
        .thenReturn(Optional.of(bankAccount));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            invocation -> {
              Transaction t = invocation.getArgument(0);
              t.setId(System.nanoTime());
              return t;
            });

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertTrue(result.success());
    assertEquals(2, result.imported()); // Two transactions
    verify(transactionRepository, times(2)).save(any(Transaction.class));
  }

  @Test
  void importTransactions_AmountWithCurrencySymbol_ParsesCorrectly() throws IOException {
    // Given - CSV with currency symbols and commas in amounts (quoted to preserve commas)
    String csv =
        """
            date,type,description,account_code,amount,direction
            2024-01-15,PAYMENT,Large Purchase,5000,"$1,234.56",DEBIT
            2024-01-15,PAYMENT,Large Purchase,1000,"$1,234.56",CREDIT
            """;

    when(accountRepository.findByCompanyAndCode(company, "5000"))
        .thenReturn(Optional.of(expenseAccount));
    when(accountRepository.findByCompanyAndCode(company, "1000"))
        .thenReturn(Optional.of(bankAccount));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            invocation -> {
              Transaction t = invocation.getArgument(0);
              t.setId(1L);
              return t;
            });

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertTrue(result.success());

    ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).save(txCaptor.capture());
    Transaction saved = txCaptor.getValue();

    assertEquals(new BigDecimal("1234.56"), saved.getLines().get(0).getAmount());
  }

  @Test
  void importTransactions_DirectionShorthand_ParsesCorrectly() throws IOException {
    // Given - CSV with DR/CR shorthand for direction
    String csv =
        """
            date,type,description,account_code,amount,direction
            2024-01-15,PAYMENT,Office Supplies,5000,100.00,DR
            2024-01-15,PAYMENT,Office Supplies,1000,100.00,CR
            """;

    when(accountRepository.findByCompanyAndCode(company, "5000"))
        .thenReturn(Optional.of(expenseAccount));
    when(accountRepository.findByCompanyAndCode(company, "1000"))
        .thenReturn(Optional.of(bankAccount));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            invocation -> {
              Transaction t = invocation.getArgument(0);
              t.setId(1L);
              return t;
            });

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertTrue(result.success());

    ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).save(txCaptor.capture());
    Transaction saved = txCaptor.getValue();

    assertEquals(Direction.DEBIT, saved.getLines().get(0).getDirection());
    assertEquals(Direction.CREDIT, saved.getLines().get(1).getDirection());
  }

  @Test
  void importTransactions_AutoPostEnabled_PostsTransaction() throws IOException {
    // Given
    String csv =
        """
            date,type,description,account_code,amount,direction
            2024-01-15,PAYMENT,Office Supplies,5000,100.00,DEBIT
            2024-01-15,PAYMENT,Office Supplies,1000,100.00,CREDIT
            """;

    when(accountRepository.findByCompanyAndCode(company, "5000"))
        .thenReturn(Optional.of(expenseAccount));
    when(accountRepository.findByCompanyAndCode(company, "1000"))
        .thenReturn(Optional.of(bankAccount));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            invocation -> {
              Transaction t = invocation.getArgument(0);
              t.setId(1L);
              return t;
            });

    // When
    ImportConfig config = new ImportConfig(true, true); // autoPost = true
    ImportResult result =
        importService.importTransactions(toInputStream(csv), company, user, config);

    // Then
    assertTrue(result.success());
    verify(postingService).postTransaction(any(Transaction.class), eq(user));
  }

  @Test
  void previewImport_DoesNotSaveAnything() throws IOException {
    // Given
    String csv =
        """
            date,type,description,account_code,amount,direction
            2024-01-15,PAYMENT,Office Supplies,5000,100.00,DEBIT
            2024-01-15,PAYMENT,Office Supplies,1000,100.00,CREDIT
            """;

    when(accountRepository.findByCompanyAndCode(company, "5000"))
        .thenReturn(Optional.of(expenseAccount));
    when(accountRepository.findByCompanyAndCode(company, "1000"))
        .thenReturn(Optional.of(bankAccount));

    // When
    ImportResult result =
        importService.previewImport(toInputStream(csv), company, ImportConfig.defaults());

    // Then
    assertTrue(result.success());
    assertEquals(1, result.imported());

    // Preview should NOT save anything
    verify(transactionRepository, never()).save(any());
    verify(auditService, never()).logEvent(any(), any(), any(), any(), any(), any());
  }

  @Test
  void importTransactions_GroupByReference_GroupsCorrectly() throws IOException {
    // Given - Two separate transactions with different references
    String csv =
        """
            date,type,description,reference,account_code,amount,direction
            2024-01-15,PAYMENT,Office Supplies,REF001,5000,100.00,DEBIT
            2024-01-15,PAYMENT,Office Supplies,REF001,1000,100.00,CREDIT
            2024-01-15,PAYMENT,Other Supplies,REF002,5000,200.00,DEBIT
            2024-01-15,PAYMENT,Other Supplies,REF002,1000,200.00,CREDIT
            """;

    when(accountRepository.findByCompanyAndCode(company, "5000"))
        .thenReturn(Optional.of(expenseAccount));
    when(accountRepository.findByCompanyAndCode(company, "1000"))
        .thenReturn(Optional.of(bankAccount));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            invocation -> {
              Transaction t = invocation.getArgument(0);
              t.setId(System.nanoTime());
              return t;
            });

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertTrue(result.success());
    assertEquals(2, result.imported()); // Two separate transactions
    verify(transactionRepository, times(2)).save(any(Transaction.class));
  }

  @Test
  void getSampleCsvContent_ReturnsValidFormat() {
    // When
    String sample = importService.getSampleCsvContent();

    // Then
    assertNotNull(sample);
    assertTrue(sample.contains("date,type,description"));
    assertTrue(sample.contains("account_code,amount,direction"));
    assertTrue(sample.contains("PAYMENT"));
    assertTrue(sample.contains("RECEIPT"));
    assertTrue(sample.contains("JOURNAL"));
  }

  @Test
  void importTransactions_TransferType_ParsesCorrectly() throws IOException {
    // Given - A transfer transaction
    String csv =
        """
            date,type,description,reference,account_code,amount,direction
            2024-01-15,TRANSFER,Bank Transfer,TRF001,1000,500.00,DEBIT
            2024-01-15,TRANSFER,Bank Transfer,TRF001,1001,500.00,CREDIT
            """;

    Account bankAccount2 = new Account(company, "1001", "Savings Account", AccountType.ASSET);
    bankAccount2.setId(10L);

    when(accountRepository.findByCompanyAndCode(company, "1000"))
        .thenReturn(Optional.of(bankAccount));
    when(accountRepository.findByCompanyAndCode(company, "1001"))
        .thenReturn(Optional.of(bankAccount2));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(
            invocation -> {
              Transaction t = invocation.getArgument(0);
              t.setId(1L);
              return t;
            });

    // When
    ImportResult result =
        importService.importTransactions(
            toInputStream(csv), company, user, ImportConfig.defaults());

    // Then
    assertTrue(result.success());

    ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).save(txCaptor.capture());
    assertEquals(TransactionType.TRANSFER, txCaptor.getValue().getType());
  }

  private InputStream toInputStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
