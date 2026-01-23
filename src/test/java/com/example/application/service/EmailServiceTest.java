package com.example.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.application.domain.*;
import com.example.application.service.EmailService.EmailAttachment;
import com.example.application.service.EmailService.EmailRequest;
import com.example.application.service.EmailService.EmailResult;

/**
 * Unit tests for EmailService. Tests email validation, result handling, and specialized email
 * methods.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

  @Mock private AuditService auditService;

  private EmailService emailService;

  private Company company;
  private User user;
  private Contact contact;

  @BeforeEach
  void setUp() {
    emailService = new EmailService(auditService);

    // Default: email disabled
    ReflectionTestUtils.setField(emailService, "emailEnabled", false);
    ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@test.local");
    ReflectionTestUtils.setField(emailService, "fromName", "Test App");

    company = new Company();
    company.setId(1L);
    company.setName("Test Company");

    user = new User("sender@example.com", "Test Sender");
    user.setId(1L);

    contact = new Contact();
    contact.setId(1L);
    contact.setCode("CUST001");
    contact.setName("Test Customer");
    contact.setEmail("customer@example.com");
  }

  @Test
  void sendEmail_ValidRequest_WhenDisabled_ReturnsDisabledStatus() {
    // Given
    EmailRequest request =
        EmailRequest.builder()
            .to("recipient@example.com", "Recipient")
            .subject("Test Subject")
            .bodyText("Test body content")
            .company(company)
            .sender(user)
            .build();

    // When
    EmailResult result = emailService.sendEmail(request);

    // Then
    assertFalse(result.success());
    assertEquals("DISABLED", result.status());
    assertEquals("Email sending is not enabled", result.message());
    assertNull(result.messageId());
  }

  @Test
  void sendEmail_ValidRequest_WhenEnabled_ReturnsQueuedStatus() {
    // Given
    ReflectionTestUtils.setField(emailService, "emailEnabled", true);

    EmailRequest request =
        EmailRequest.builder()
            .to("recipient@example.com", "Recipient")
            .subject("Test Subject")
            .bodyText("Test body content")
            .company(company)
            .sender(user)
            .build();

    // When
    EmailResult result = emailService.sendEmail(request);

    // Then
    assertTrue(result.success());
    assertEquals("QUEUED", result.status());
    assertNotNull(result.messageId());

    // Verify audit logging
    verify(auditService)
        .logEvent(
            eq(company),
            eq(user),
            eq("EMAIL_SENT"),
            eq("EMAIL"),
            isNull(),
            contains("recipient@example.com"));
  }

  @Test
  void sendEmail_NullRecipient_ReturnsInvalidRecipient() {
    // Given
    EmailRequest request =
        EmailRequest.builder().to(null).subject("Test Subject").bodyText("Test body").build();

    // When
    EmailResult result = emailService.sendEmail(request);

    // Then
    assertFalse(result.success());
    assertEquals("INVALID_RECIPIENT", result.status());
    assertTrue(result.message().contains("required"));
  }

  @Test
  void sendEmail_BlankRecipient_ReturnsInvalidRecipient() {
    // Given
    EmailRequest request =
        EmailRequest.builder().to("   ").subject("Test Subject").bodyText("Test body").build();

    // When
    EmailResult result = emailService.sendEmail(request);

    // Then
    assertFalse(result.success());
    assertEquals("INVALID_RECIPIENT", result.status());
  }

  @Test
  void sendEmail_InvalidEmailFormat_ReturnsInvalidRecipient() {
    // Given
    EmailRequest request =
        EmailRequest.builder()
            .to("not-an-email")
            .subject("Test Subject")
            .bodyText("Test body")
            .build();

    // When
    EmailResult result = emailService.sendEmail(request);

    // Then
    assertFalse(result.success());
    assertEquals("INVALID_RECIPIENT", result.status());
    assertTrue(result.message().contains("Invalid email"));
  }

  @Test
  void sendEmail_MissingSubject_ReturnsFailed() {
    // Given
    EmailRequest request =
        EmailRequest.builder().to("valid@example.com").subject(null).bodyText("Test body").build();

    // When
    EmailResult result = emailService.sendEmail(request);

    // Then
    assertFalse(result.success());
    assertEquals("FAILED", result.status());
    assertTrue(result.message().contains("subject"));
  }

  @Test
  void sendEmail_MissingBody_ReturnsFailed() {
    // Given
    EmailRequest request =
        EmailRequest.builder()
            .to("valid@example.com")
            .subject("Test Subject")
            .bodyText(null)
            .bodyHtml(null)
            .build();

    // When
    EmailResult result = emailService.sendEmail(request);

    // Then
    assertFalse(result.success());
    assertEquals("FAILED", result.status());
    assertTrue(result.message().contains("body"));
  }

  @Test
  void sendEmail_HtmlBodyOnly_Succeeds() {
    // Given
    ReflectionTestUtils.setField(emailService, "emailEnabled", true);

    EmailRequest request =
        EmailRequest.builder()
            .to("valid@example.com")
            .subject("Test Subject")
            .bodyHtml("<h1>HTML Content</h1>")
            .company(company)
            .sender(user)
            .build();

    // When
    EmailResult result = emailService.sendEmail(request);

    // Then
    assertTrue(result.success());
    assertEquals("QUEUED", result.status());
  }

  @Test
  void sendEmail_WithAttachments_LogsAttachmentCount() {
    // Given
    ReflectionTestUtils.setField(emailService, "emailEnabled", true);

    EmailAttachment attachment =
        new EmailAttachment("document.pdf", "application/pdf", "content".getBytes());

    EmailRequest request =
        EmailRequest.builder()
            .to("valid@example.com")
            .subject("Test Subject")
            .bodyText("Test body")
            .attachments(List.of(attachment))
            .company(company)
            .sender(user)
            .build();

    // When
    EmailResult result = emailService.sendEmail(request);

    // Then
    assertTrue(result.success());
  }

  @Test
  void sendInvoice_ValidInvoice_SendsEmail() {
    // Given
    ReflectionTestUtils.setField(emailService, "emailEnabled", true);

    SalesInvoice invoice = new SalesInvoice();
    invoice.setId(1L);
    invoice.setInvoiceNumber("INV-001");
    invoice.setCompany(company);
    invoice.setContact(contact);
    invoice.setDueDate(LocalDate.now().plusDays(30));
    invoice.setTotal(new BigDecimal("1000.00"));

    byte[] pdfContent = "PDF content".getBytes();

    // When
    EmailResult result = emailService.sendInvoice(invoice, pdfContent, user);

    // Then
    assertTrue(result.success());
    assertEquals("QUEUED", result.status());
  }

  @Test
  void sendInvoice_NoContact_ReturnsFailed() {
    // Given
    SalesInvoice invoice = new SalesInvoice();
    invoice.setId(1L);
    invoice.setInvoiceNumber("INV-001");
    invoice.setCompany(company);
    invoice.setContact(null);

    byte[] pdfContent = "PDF content".getBytes();

    // When
    EmailResult result = emailService.sendInvoice(invoice, pdfContent, user);

    // Then
    assertFalse(result.success());
    assertEquals("FAILED", result.status());
    assertTrue(result.message().contains("customer"));
  }

  @Test
  void sendInvoice_ContactNoEmail_ReturnsInvalidRecipient() {
    // Given
    Contact noEmailContact = new Contact();
    noEmailContact.setId(2L);
    noEmailContact.setName("No Email Customer");
    noEmailContact.setEmail(null);

    SalesInvoice invoice = new SalesInvoice();
    invoice.setId(1L);
    invoice.setInvoiceNumber("INV-001");
    invoice.setCompany(company);
    invoice.setContact(noEmailContact);

    byte[] pdfContent = "PDF content".getBytes();

    // When
    EmailResult result = emailService.sendInvoice(invoice, pdfContent, user);

    // Then
    assertFalse(result.success());
    assertEquals("INVALID_RECIPIENT", result.status());
  }

  @Test
  void sendStatement_ValidContact_SendsEmail() {
    // Given
    ReflectionTestUtils.setField(emailService, "emailEnabled", true);
    byte[] pdfContent = "Statement PDF".getBytes();

    // When
    EmailResult result = emailService.sendStatement(contact, company, pdfContent, user);

    // Then
    assertTrue(result.success());
    assertEquals("QUEUED", result.status());
  }

  @Test
  void sendRemittanceAdvice_ValidContact_SendsEmail() {
    // Given
    ReflectionTestUtils.setField(emailService, "emailEnabled", true);
    byte[] pdfContent = "Remittance PDF".getBytes();

    // When
    EmailResult result = emailService.sendRemittanceAdvice(contact, company, pdfContent, user);

    // Then
    assertTrue(result.success());
    assertEquals("QUEUED", result.status());
  }

  @Test
  void sendReport_ValidRequest_SendsEmail() {
    // Given
    ReflectionTestUtils.setField(emailService, "emailEnabled", true);
    byte[] pdfContent = "Report PDF".getBytes();

    // When
    EmailResult result =
        emailService.sendReport(
            "accountant@example.com", "Profit and Loss", pdfContent, company, user);

    // Then
    assertTrue(result.success());
    assertEquals("QUEUED", result.status());
  }

  @Test
  void isEmailEnabled_ReturnsConfiguredValue() {
    // Default is false
    assertFalse(emailService.isEmailEnabled());

    // Enable
    ReflectionTestUtils.setField(emailService, "emailEnabled", true);
    assertTrue(emailService.isEmailEnabled());
  }

  @Test
  void getFromAddress_ReturnsConfiguredValue() {
    assertEquals("noreply@test.local", emailService.getFromAddress());
  }

  @Test
  void getFromName_ReturnsConfiguredValue() {
    assertEquals("Test App", emailService.getFromName());
  }

  @Test
  void emailRequest_Builder_SetsAllFields() {
    // Given/When
    EmailRequest request =
        EmailRequest.builder()
            .to("to@example.com", "To Name")
            .subject("Subject")
            .bodyText("Text")
            .bodyHtml("<p>HTML</p>")
            .company(company)
            .sender(user)
            .build();

    // Then
    assertEquals("to@example.com", request.toAddress());
    assertEquals("To Name", request.toName());
    assertEquals("Subject", request.subject());
    assertEquals("Text", request.bodyText());
    assertEquals("<p>HTML</p>", request.bodyHtml());
    assertEquals(company, request.company());
    assertEquals(user, request.sender());
  }

  @Test
  void emailRequest_ToContact_SetsContactEmailAndName() {
    // Given/When
    EmailRequest request =
        EmailRequest.builder().toContact(contact).subject("Test").bodyText("Content").build();

    // Then
    assertEquals("customer@example.com", request.toAddress());
    assertEquals("Test Customer", request.toName());
  }

  @Test
  void emailResult_FactoryMethods_CreateCorrectResults() {
    // Queued
    EmailResult queued = EmailResult.queued("msg-123");
    assertTrue(queued.success());
    assertEquals("QUEUED", queued.status());
    assertEquals("msg-123", queued.messageId());

    // Sent
    EmailResult sent = EmailResult.sent("msg-456");
    assertTrue(sent.success());
    assertEquals("SENT", sent.status());

    // Disabled
    EmailResult disabled = EmailResult.disabled();
    assertFalse(disabled.success());
    assertEquals("DISABLED", disabled.status());

    // Failed
    EmailResult failed = EmailResult.failed("Error message");
    assertFalse(failed.success());
    assertEquals("FAILED", failed.status());
    assertEquals("Error message", failed.message());

    // Invalid Recipient
    EmailResult invalid = EmailResult.invalidRecipient("Bad email");
    assertFalse(invalid.success());
    assertEquals("INVALID_RECIPIENT", invalid.status());
  }
}
