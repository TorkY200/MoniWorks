package com.example.application.service;

import com.example.application.domain.Company;
import com.example.application.domain.Contact;
import com.example.application.domain.SalesInvoice;
import com.example.application.domain.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for sending emails with PDF attachments.
 *
 * v1 Implementation: This is a stub implementation that logs email requests
 * and prepares email content but does not actually send emails. The interface
 * is designed to be easily replaced with a real implementation (SMTP, SendGrid, etc.)
 * when email sending is required.
 *
 * Current behavior:
 * - Logs all email requests for audit/debugging
 * - Validates email addresses and content
 * - Returns EmailResult with success/failure status
 * - Actual sending is disabled (returns "queued" status)
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${moniworks.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${moniworks.email.from-address:noreply@moniworks.local}")
    private String fromAddress;

    @Value("${moniworks.email.from-name:MoniWorks}")
    private String fromName;

    private final AuditService auditService;

    public EmailService(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Result of an email send attempt.
     */
    public record EmailResult(
        boolean success,
        String status,
        String message,
        String messageId
    ) {
        public static EmailResult queued(String messageId) {
            return new EmailResult(true, "QUEUED", "Email queued for delivery", messageId);
        }

        public static EmailResult sent(String messageId) {
            return new EmailResult(true, "SENT", "Email sent successfully", messageId);
        }

        public static EmailResult disabled() {
            return new EmailResult(false, "DISABLED", "Email sending is not enabled", null);
        }

        public static EmailResult failed(String reason) {
            return new EmailResult(false, "FAILED", reason, null);
        }

        public static EmailResult invalidRecipient(String reason) {
            return new EmailResult(false, "INVALID_RECIPIENT", reason, null);
        }
    }

    /**
     * Email request builder for constructing email messages.
     */
    public record EmailRequest(
        String toAddress,
        String toName,
        String subject,
        String bodyText,
        String bodyHtml,
        List<EmailAttachment> attachments,
        Company company,
        User sender
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String toAddress;
            private String toName;
            private String subject;
            private String bodyText;
            private String bodyHtml;
            private List<EmailAttachment> attachments = List.of();
            private Company company;
            private User sender;

            public Builder to(String address, String name) {
                this.toAddress = address;
                this.toName = name;
                return this;
            }

            public Builder to(String address) {
                this.toAddress = address;
                return this;
            }

            public Builder toContact(Contact contact) {
                this.toAddress = contact.getEmail();
                this.toName = contact.getName();
                return this;
            }

            public Builder subject(String subject) {
                this.subject = subject;
                return this;
            }

            public Builder bodyText(String text) {
                this.bodyText = text;
                return this;
            }

            public Builder bodyHtml(String html) {
                this.bodyHtml = html;
                return this;
            }

            public Builder attachments(List<EmailAttachment> attachments) {
                this.attachments = attachments != null ? attachments : List.of();
                return this;
            }

            public Builder attachment(EmailAttachment attachment) {
                this.attachments = List.of(attachment);
                return this;
            }

            public Builder company(Company company) {
                this.company = company;
                return this;
            }

            public Builder sender(User sender) {
                this.sender = sender;
                return this;
            }

            public EmailRequest build() {
                return new EmailRequest(toAddress, toName, subject, bodyText, bodyHtml,
                    attachments, company, sender);
            }
        }
    }

    /**
     * Email attachment record.
     */
    public record EmailAttachment(
        String filename,
        String mimeType,
        byte[] content
    ) {}

    /**
     * Send an email with the given request parameters.
     *
     * @param request The email request containing recipient, subject, body, and attachments
     * @return EmailResult indicating success or failure
     */
    public EmailResult sendEmail(EmailRequest request) {
        // Validate request
        if (request.toAddress() == null || request.toAddress().isBlank()) {
            return EmailResult.invalidRecipient("Recipient email address is required");
        }

        if (!isValidEmail(request.toAddress())) {
            return EmailResult.invalidRecipient("Invalid email address: " + request.toAddress());
        }

        if (request.subject() == null || request.subject().isBlank()) {
            return EmailResult.failed("Email subject is required");
        }

        if ((request.bodyText() == null || request.bodyText().isBlank())
            && (request.bodyHtml() == null || request.bodyHtml().isBlank())) {
            return EmailResult.failed("Email body is required");
        }

        // Check if email is enabled
        if (!emailEnabled) {
            log.info("Email sending disabled. Would send to: {} subject: {}",
                request.toAddress(), request.subject());

            // Log the audit event even for disabled emails
            if (request.company() != null) {
                auditService.logEvent(
                    request.company(),
                    request.sender(),
                    "EMAIL_QUEUED",
                    "EMAIL",
                    null,
                    String.format("Email queued (sending disabled) to: %s, subject: %s",
                        request.toAddress(), request.subject())
                );
            }

            return EmailResult.disabled();
        }

        // Generate a message ID for tracking
        String messageId = generateMessageId();

        try {
            // In v1, we just log the email request
            // In a real implementation, this would send via SMTP, SendGrid, etc.
            log.info("Email queued [{}]: to={}, subject={}, attachments={}",
                messageId, request.toAddress(), request.subject(),
                request.attachments() != null ? request.attachments().size() : 0);

            // Log audit event
            if (request.company() != null) {
                auditService.logEvent(
                    request.company(),
                    request.sender(),
                    "EMAIL_SENT",
                    "EMAIL",
                    null,
                    String.format("Email sent to: %s, subject: %s, messageId: %s",
                        request.toAddress(), request.subject(), messageId)
                );
            }

            return EmailResult.queued(messageId);

        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", request.toAddress(), e.getMessage(), e);
            return EmailResult.failed("Failed to send email: " + e.getMessage());
        }
    }

    /**
     * Send an invoice PDF to a customer.
     *
     * @param invoice The invoice to send
     * @param pdfContent The PDF content bytes
     * @param sender The user sending the email
     * @return EmailResult indicating success or failure
     */
    public EmailResult sendInvoice(SalesInvoice invoice, byte[] pdfContent, User sender) {
        Contact customer = invoice.getContact();

        if (customer == null) {
            return EmailResult.failed("Invoice has no associated customer");
        }

        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            return EmailResult.invalidRecipient("Customer has no email address");
        }

        String subject = String.format("Invoice %s from %s",
            invoice.getInvoiceNumber(),
            invoice.getCompany().getName());

        String bodyText = buildInvoiceEmailBody(invoice);

        EmailAttachment attachment = new EmailAttachment(
            "Invoice-" + invoice.getInvoiceNumber() + ".pdf",
            "application/pdf",
            pdfContent
        );

        EmailRequest request = EmailRequest.builder()
            .toContact(customer)
            .subject(subject)
            .bodyText(bodyText)
            .attachment(attachment)
            .company(invoice.getCompany())
            .sender(sender)
            .build();

        return sendEmail(request);
    }

    /**
     * Send a statement to a customer.
     *
     * @param contact The customer
     * @param company The company sending the statement
     * @param pdfContent The statement PDF content
     * @param sender The user sending the email
     * @return EmailResult indicating success or failure
     */
    public EmailResult sendStatement(Contact contact, Company company, byte[] pdfContent, User sender) {
        if (contact.getEmail() == null || contact.getEmail().isBlank()) {
            return EmailResult.invalidRecipient("Customer has no email address");
        }

        String subject = String.format("Statement from %s", company.getName());

        String bodyText = String.format("""
            Dear %s,

            Please find attached your statement from %s.

            If you have any questions about this statement, please contact us.

            Best regards,
            %s
            """,
            contact.getName(),
            company.getName(),
            company.getName());

        EmailAttachment attachment = new EmailAttachment(
            "Statement-" + contact.getCode() + ".pdf",
            "application/pdf",
            pdfContent
        );

        EmailRequest request = EmailRequest.builder()
            .toContact(contact)
            .subject(subject)
            .bodyText(bodyText)
            .attachment(attachment)
            .company(company)
            .sender(sender)
            .build();

        return sendEmail(request);
    }

    /**
     * Send a remittance advice to a supplier.
     *
     * @param contact The supplier
     * @param company The company sending the remittance
     * @param pdfContent The remittance advice PDF content
     * @param sender The user sending the email
     * @return EmailResult indicating success or failure
     */
    public EmailResult sendRemittanceAdvice(Contact contact, Company company, byte[] pdfContent, User sender) {
        if (contact.getEmail() == null || contact.getEmail().isBlank()) {
            return EmailResult.invalidRecipient("Supplier has no email address");
        }

        String subject = String.format("Remittance Advice from %s", company.getName());

        String bodyText = String.format("""
            Dear %s,

            Please find attached remittance advice for a payment from %s.

            If you have any questions, please contact us.

            Best regards,
            %s
            """,
            contact.getName(),
            company.getName(),
            company.getName());

        EmailAttachment attachment = new EmailAttachment(
            "Remittance-Advice.pdf",
            "application/pdf",
            pdfContent
        );

        EmailRequest request = EmailRequest.builder()
            .toContact(contact)
            .subject(subject)
            .bodyText(bodyText)
            .attachment(attachment)
            .company(company)
            .sender(sender)
            .build();

        return sendEmail(request);
    }

    /**
     * Send a report PDF to the specified email address.
     *
     * @param toAddress Recipient email address
     * @param reportName The name of the report
     * @param pdfContent The report PDF content
     * @param company The company context
     * @param sender The user sending the email
     * @return EmailResult indicating success or failure
     */
    public EmailResult sendReport(String toAddress, String reportName, byte[] pdfContent,
                                  Company company, User sender) {
        String subject = String.format("%s - %s", reportName, company.getName());

        String bodyText = String.format("""
            Please find attached the %s report from %s.

            This report was generated on %s.

            Best regards,
            %s
            """,
            reportName,
            company.getName(),
            java.time.LocalDate.now().toString(),
            company.getName());

        EmailAttachment attachment = new EmailAttachment(
            reportName.replaceAll("\\s+", "-") + ".pdf",
            "application/pdf",
            pdfContent
        );

        EmailRequest request = EmailRequest.builder()
            .to(toAddress)
            .subject(subject)
            .bodyText(bodyText)
            .attachment(attachment)
            .company(company)
            .sender(sender)
            .build();

        return sendEmail(request);
    }

    /**
     * Check if email sending is enabled.
     */
    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    /**
     * Get the configured from address.
     */
    public String getFromAddress() {
        return fromAddress;
    }

    /**
     * Get the configured from name.
     */
    public String getFromName() {
        return fromName;
    }

    // Private helper methods

    private String buildInvoiceEmailBody(SalesInvoice invoice) {
        return String.format("""
            Dear %s,

            Please find attached invoice %s for the amount of %s.

            Due date: %s

            If you have any questions about this invoice, please contact us.

            Best regards,
            %s
            """,
            invoice.getContact().getName(),
            invoice.getInvoiceNumber(),
            formatAmount(invoice.getTotal()),
            invoice.getDueDate() != null ? invoice.getDueDate().toString() : "Upon receipt",
            invoice.getCompany().getName());
    }

    private String formatAmount(java.math.BigDecimal amount) {
        if (amount == null) {
            return "$0.00";
        }
        return String.format("$%,.2f", amount);
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        // Basic email validation regex
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }

    private String generateMessageId() {
        return String.format("%s-%d",
            java.util.UUID.randomUUID().toString().substring(0, 8),
            System.currentTimeMillis());
    }
}
