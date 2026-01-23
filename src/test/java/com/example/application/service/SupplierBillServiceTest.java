package com.example.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.domain.*;
import com.example.application.domain.SupplierBill.BillStatus;
import com.example.application.domain.SupplierBill.BillType;
import com.example.application.repository.SupplierBillLineRepository;
import com.example.application.repository.SupplierBillRepository;
import com.example.application.repository.TaxCodeRepository;

/** Unit tests for SupplierBillService, focusing on debit note functionality. */
@ExtendWith(MockitoExtension.class)
class SupplierBillServiceTest {

  @Mock private SupplierBillRepository billRepository;

  @Mock private SupplierBillLineRepository lineRepository;

  @Mock private TaxCodeRepository taxCodeRepository;

  @Mock private AccountService accountService;

  @Mock private TransactionService transactionService;

  @Mock private PostingService postingService;

  @Mock private AuditService auditService;

  private SupplierBillService billService;

  private Company company;
  private Contact supplier;
  private Account expenseAccount;
  private Account apAccount;
  private User user;
  private SupplierBill postedBill;

  @BeforeEach
  void setUp() {
    billService =
        new SupplierBillService(
            billRepository,
            lineRepository,
            taxCodeRepository,
            accountService,
            transactionService,
            postingService,
            auditService);

    company = new Company();
    company.setId(1L);
    company.setName("Test Company");

    supplier = new Contact();
    supplier.setId(1L);
    supplier.setCode("SUP001");
    supplier.setName("Test Supplier");

    expenseAccount = new Account();
    expenseAccount.setId(1L);
    expenseAccount.setCode("5000");
    expenseAccount.setName("Office Supplies");
    expenseAccount.setType(Account.AccountType.EXPENSE);

    apAccount = new Account();
    apAccount.setId(2L);
    apAccount.setCode("2100");
    apAccount.setName("Accounts Payable");
    apAccount.setType(Account.AccountType.LIABILITY);

    user = new User("admin@test.com", "Admin User");
    user.setId(1L);

    // Create a posted bill for debit note tests
    postedBill = new SupplierBill();
    postedBill.setId(1L);
    postedBill.setCompany(company);
    postedBill.setBillNumber("BILL-1");
    postedBill.setContact(supplier);
    postedBill.setBillDate(LocalDate.now().minusDays(30));
    postedBill.setDueDate(LocalDate.now());
    postedBill.setStatus(BillStatus.POSTED);
    postedBill.setType(BillType.BILL);
    postedBill.setCurrency("NZD");

    // Add a line to the posted bill
    SupplierBillLine line =
        new SupplierBillLine(expenseAccount, BigDecimal.valueOf(2), BigDecimal.valueOf(100));
    line.setId(1L);
    line.setDescription("Test Expense");
    line.setTaxCode("GST");
    line.setTaxRate(BigDecimal.valueOf(15));
    line.calculateTotals();
    postedBill.addLine(line);
    postedBill.recalculateTotals();
  }

  @Test
  void createDebitNote_FullDebit_Success() {
    // Given
    when(billRepository.existsByCompanyAndBillNumber(company, "DN-BILL-1")).thenReturn(false);
    when(billRepository.save(any(SupplierBill.class)))
        .thenAnswer(
            invocation -> {
              SupplierBill dn = invocation.getArgument(0);
              dn.setId(2L);
              return dn;
            });

    // When
    SupplierBill debitNote = billService.createDebitNote(postedBill, user, true);

    // Then
    assertNotNull(debitNote);
    assertEquals("DN-BILL-1", debitNote.getBillNumber());
    assertEquals(BillType.DEBIT_NOTE, debitNote.getType());
    assertEquals(BillStatus.DRAFT, debitNote.getStatus());
    assertEquals(postedBill, debitNote.getOriginalBill());
    assertEquals(supplier, debitNote.getContact());
    assertEquals(LocalDate.now(), debitNote.getBillDate());
    assertEquals(LocalDate.now(), debitNote.getDueDate());

    // Debit note should have copied lines
    assertEquals(1, debitNote.getLines().size());
    SupplierBillLine debitLine = debitNote.getLines().get(0);
    assertEquals("Test Expense", debitLine.getDescription());
    assertEquals(BigDecimal.valueOf(2), debitLine.getQuantity());
    assertEquals(BigDecimal.valueOf(100), debitLine.getUnitPrice());

    verify(auditService)
        .logEvent(
            eq(company),
            eq(user),
            eq("DEBIT_NOTE_CREATED"),
            eq("SupplierBill"),
            anyLong(),
            anyString());
  }

  @Test
  void createDebitNote_PartialDebit_CreatesEmptyDraft() {
    // Given
    when(billRepository.existsByCompanyAndBillNumber(company, "DN-BILL-1")).thenReturn(false);
    when(billRepository.save(any(SupplierBill.class)))
        .thenAnswer(
            invocation -> {
              SupplierBill dn = invocation.getArgument(0);
              dn.setId(2L);
              return dn;
            });

    // When
    SupplierBill debitNote = billService.createDebitNote(postedBill, user, false);

    // Then
    assertNotNull(debitNote);
    assertEquals(BillType.DEBIT_NOTE, debitNote.getType());
    assertEquals(BillStatus.DRAFT, debitNote.getStatus());
    assertTrue(debitNote.getLines().isEmpty()); // No lines copied for partial debit
  }

  @Test
  void createDebitNote_DraftBill_ThrowsException() {
    // Given
    postedBill.setStatus(BillStatus.DRAFT);

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> billService.createDebitNote(postedBill, user, true));
    assertEquals("Can only create debit notes against posted bills", exception.getMessage());
  }

  @Test
  void createDebitNote_AgainstDebitNote_ThrowsException() {
    // Given
    postedBill.setType(BillType.DEBIT_NOTE);

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> billService.createDebitNote(postedBill, user, true));
    assertEquals("Cannot create a debit note against another debit note", exception.getMessage());
  }

  @Test
  void createDebitNote_SecondDebitNote_IncrementsSuffix() {
    // Given - One debit note already exists
    when(billRepository.existsByCompanyAndBillNumber(company, "DN-BILL-1"))
        .thenReturn(true); // First number exists
    when(billRepository.existsByCompanyAndBillNumber(company, "DN-BILL-1-1"))
        .thenReturn(false); // Suffix 1 available
    when(billRepository.save(any(SupplierBill.class)))
        .thenAnswer(
            invocation -> {
              SupplierBill dn = invocation.getArgument(0);
              dn.setId(3L);
              return dn;
            });

    // When
    SupplierBill debitNote = billService.createDebitNote(postedBill, user, true);

    // Then
    assertEquals("DN-BILL-1-1", debitNote.getBillNumber());
  }

  @Test
  void postDebitNote_ValidDebitNote_PostsReversedEntries() {
    // Given
    SupplierBill debitNote = new SupplierBill();
    debitNote.setId(2L);
    debitNote.setCompany(company);
    debitNote.setBillNumber("DN-BILL-1");
    debitNote.setContact(supplier);
    debitNote.setBillDate(LocalDate.now());
    debitNote.setDueDate(LocalDate.now());
    debitNote.setStatus(BillStatus.DRAFT);
    debitNote.setType(BillType.DEBIT_NOTE);
    debitNote.setOriginalBill(postedBill);
    debitNote.setCurrency("NZD");

    // Add a line
    SupplierBillLine debitLine =
        new SupplierBillLine(expenseAccount, BigDecimal.ONE, BigDecimal.valueOf(100));
    debitLine.setDescription("Debit for expense");
    debitLine.setTaxCode("GST");
    debitLine.setTaxRate(BigDecimal.valueOf(15));
    debitLine.calculateTotals();
    debitNote.addLine(debitLine);
    debitNote.recalculateTotals();

    when(accountService.findByCompanyAndCode(company, "2100")).thenReturn(Optional.of(apAccount));

    Account gstAccount = new Account();
    gstAccount.setId(3L);
    gstAccount.setCode("1150");
    gstAccount.setName("GST Paid");
    when(accountService.findByCompanyAndCode(company, "1150")).thenReturn(Optional.of(gstAccount));

    Transaction mockTransaction = new Transaction();
    mockTransaction.setId(1L);
    when(transactionService.createTransaction(any(), any(), any(), anyString(), any()))
        .thenReturn(mockTransaction);
    when(postingService.postTransaction(any(), any())).thenReturn(mockTransaction);
    when(billRepository.save(any(SupplierBill.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    SupplierBill posted = billService.postDebitNote(debitNote, user);

    // Then
    assertEquals(BillStatus.POSTED, posted.getStatus());
    assertNotNull(posted.getPostedAt());
    assertEquals(mockTransaction, posted.getPostedTransaction());

    // Verify reversed posting was created
    verify(transactionService)
        .createTransaction(
            eq(company),
            eq(Transaction.TransactionType.JOURNAL),
            eq(debitNote.getBillDate()),
            contains("Debit Note"),
            eq(user));
  }

  @Test
  void postDebitNote_ExceedsBalance_ThrowsException() {
    // Given
    postedBill.setAmountPaid(BigDecimal.valueOf(180)); // Most of it paid already
    // Balance is now 230 - 180 = 50 (approx)

    SupplierBill debitNote = new SupplierBill();
    debitNote.setId(2L);
    debitNote.setCompany(company);
    debitNote.setBillNumber("DN-BILL-1");
    debitNote.setContact(supplier);
    debitNote.setBillDate(LocalDate.now());
    debitNote.setDueDate(LocalDate.now());
    debitNote.setStatus(BillStatus.DRAFT);
    debitNote.setType(BillType.DEBIT_NOTE);
    debitNote.setOriginalBill(postedBill);

    // Add a line that would exceed the remaining balance
    SupplierBillLine debitLine =
        new SupplierBillLine(expenseAccount, BigDecimal.ONE, BigDecimal.valueOf(100));
    debitLine.setTaxCode("GST");
    debitLine.setTaxRate(BigDecimal.valueOf(15));
    debitLine.calculateTotals();
    debitNote.addLine(debitLine);
    debitNote.recalculateTotals();

    // When & Then
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> billService.postDebitNote(debitNote, user));
    assertTrue(exception.getMessage().contains("exceeds bill remaining balance"));
  }

  @Test
  void postDebitNote_NotDebitNote_ThrowsException() {
    // Given - Regular bill, not a debit note
    SupplierBill regularBill = new SupplierBill();
    regularBill.setId(2L);
    regularBill.setType(BillType.BILL);
    regularBill.setStatus(BillStatus.DRAFT);

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> billService.postDebitNote(regularBill, user));
    assertEquals("This is not a debit note", exception.getMessage());
  }

  @Test
  void postDebitNote_AlreadyPosted_ThrowsException() {
    // Given
    SupplierBill debitNote = new SupplierBill();
    debitNote.setId(2L);
    debitNote.setType(BillType.DEBIT_NOTE);
    debitNote.setStatus(BillStatus.POSTED);

    // When & Then
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> billService.postDebitNote(debitNote, user));
    assertEquals("Debit note is not in draft status", exception.getMessage());
  }

  @Test
  void postDebitNote_NoLines_ThrowsException() {
    // Given
    SupplierBill debitNote = new SupplierBill();
    debitNote.setId(2L);
    debitNote.setType(BillType.DEBIT_NOTE);
    debitNote.setStatus(BillStatus.DRAFT);
    debitNote.setOriginalBill(postedBill);
    // No lines added

    // When & Then
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> billService.postDebitNote(debitNote, user));
    assertEquals("Debit note has no lines", exception.getMessage());
  }

  @Test
  void findDebitNotesForBill_ReturnsDebitNotes() {
    // Given
    SupplierBill debitNote1 = new SupplierBill();
    debitNote1.setId(2L);
    debitNote1.setBillNumber("DN-BILL-1");
    debitNote1.setType(BillType.DEBIT_NOTE);

    SupplierBill debitNote2 = new SupplierBill();
    debitNote2.setId(3L);
    debitNote2.setBillNumber("DN-BILL-1-1");
    debitNote2.setType(BillType.DEBIT_NOTE);

    when(billRepository.findByOriginalBill(postedBill)).thenReturn(List.of(debitNote1, debitNote2));

    // When
    List<SupplierBill> debitNotes = billService.findDebitNotesForBill(postedBill);

    // Then
    assertEquals(2, debitNotes.size());
    assertEquals("DN-BILL-1", debitNotes.get(0).getBillNumber());
    assertEquals("DN-BILL-1-1", debitNotes.get(1).getBillNumber());
  }

  @Test
  void voidDebitNote_PostedDebitNote_VoidsSuccessfully() {
    // Given - a posted debit note
    SupplierBill debitNote = new SupplierBill();
    debitNote.setId(2L);
    debitNote.setCompany(company);
    debitNote.setBillNumber("DN-BILL-1");
    debitNote.setContact(supplier);
    debitNote.setBillDate(LocalDate.now());
    debitNote.setDueDate(LocalDate.now());
    debitNote.setStatus(BillStatus.POSTED);
    debitNote.setType(BillType.DEBIT_NOTE);
    debitNote.setOriginalBill(postedBill);

    // Set up the debit note total
    SupplierBillLine debitLine =
        new SupplierBillLine(expenseAccount, BigDecimal.ONE, BigDecimal.valueOf(100));
    debitLine.setTaxCode("GST");
    debitLine.setTaxRate(BigDecimal.valueOf(15));
    debitLine.calculateTotals();
    debitNote.addLine(debitLine);
    debitNote.recalculateTotals();

    // Original bill has the debit applied
    postedBill.setAmountPaid(BigDecimal.valueOf(100));

    Transaction postedTransaction = new Transaction();
    postedTransaction.setId(1L);
    debitNote.setPostedTransaction(postedTransaction);

    when(billRepository.save(any(SupplierBill.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    SupplierBill voided = billService.voidDebitNote(debitNote, user, "Supplier dispute");

    // Then
    assertEquals(BillStatus.VOID, voided.getStatus());
    verify(postingService).reverseTransaction(postedTransaction, user, "Supplier dispute");
    // Original bill's amountPaid should be reduced
    assertEquals(0, postedBill.getAmountPaid().compareTo(BigDecimal.ZERO));
    verify(billRepository, times(2)).save(any(SupplierBill.class)); // Debit note + original
    verify(auditService)
        .logEvent(
            eq(company),
            eq(user),
            eq("DEBIT_NOTE_VOIDED"),
            eq("SupplierBill"),
            eq(2L),
            contains("Voided debit note"));
  }

  @Test
  void voidDebitNote_NotDebitNote_ThrowsException() {
    // Given - regular bill, not a debit note
    postedBill.setStatus(BillStatus.POSTED);

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> billService.voidDebitNote(postedBill, user, "Test"));
    assertEquals("This is not a debit note", exception.getMessage());
  }

  @Test
  void voidDebitNote_DraftDebitNote_ThrowsException() {
    // Given - draft debit note
    SupplierBill debitNote = new SupplierBill();
    debitNote.setId(2L);
    debitNote.setType(BillType.DEBIT_NOTE);
    debitNote.setStatus(BillStatus.DRAFT);

    // When & Then
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> billService.voidDebitNote(debitNote, user, "Test"));
    assertEquals("Can only void posted debit notes", exception.getMessage());
  }

  @Test
  void voidDebitNote_NoPostedTransaction_StillVoids() {
    // Given - posted debit note without posted transaction (edge case)
    SupplierBill debitNote = new SupplierBill();
    debitNote.setId(2L);
    debitNote.setCompany(company);
    debitNote.setBillNumber("DN-BILL-1");
    debitNote.setStatus(BillStatus.POSTED);
    debitNote.setType(BillType.DEBIT_NOTE);
    debitNote.setOriginalBill(postedBill);
    debitNote.setPostedTransaction(null); // No transaction

    when(billRepository.save(any(SupplierBill.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    SupplierBill voided = billService.voidDebitNote(debitNote, user, null);

    // Then
    assertEquals(BillStatus.VOID, voided.getStatus());
    verify(postingService, never()).reverseTransaction(any(), any(), any());
  }
}
