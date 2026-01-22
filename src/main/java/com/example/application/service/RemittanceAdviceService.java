package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.domain.AttachmentLink.EntityType;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for generating remittance advice PDFs.
 * Creates one remittance advice per supplier in a payment run.
 */
@Service
@Transactional
public class RemittanceAdviceService {

    private static final Logger log = LoggerFactory.getLogger(RemittanceAdviceService.class);

    private final AttachmentService attachmentService;

    // Fonts
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font HEADING_FONT = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL);
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font TABLE_CELL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL);

    // Colors
    private static final Color HEADER_BG = new Color(52, 73, 94); // Dark blue-gray
    private static final Color ALT_ROW_BG = new Color(245, 247, 249); // Light gray

    private final NumberFormat currencyFormat;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy");

    public RemittanceAdviceService(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "NZ"));
    }

    /**
     * Generates a combined remittance advice PDF for all suppliers in a payment run.
     * Each supplier gets their own page in the PDF.
     *
     * @param paymentRun The completed payment run
     * @param runBills List of bills included in the run
     * @param creator User who created the run
     * @return The created attachment containing the PDF
     */
    public Attachment generateRemittanceAdvice(PaymentRun paymentRun,
                                                List<PaymentRunService.PaymentRunBill> runBills,
                                                User creator) {
        Company company = paymentRun.getCompany();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Group bills by supplier
            Map<Contact, List<PaymentRunService.PaymentRunBill>> bySupplier = runBills.stream()
                .collect(Collectors.groupingBy(rb -> rb.bill().getContact()));

            boolean firstPage = true;
            for (Map.Entry<Contact, List<PaymentRunService.PaymentRunBill>> entry : bySupplier.entrySet()) {
                if (!firstPage) {
                    document.newPage();
                }
                firstPage = false;

                Contact supplier = entry.getKey();
                List<PaymentRunService.PaymentRunBill> supplierBills = entry.getValue();

                addSupplierRemittance(document, company, paymentRun, supplier, supplierBills);
            }

            document.close();

            // Store the PDF as an attachment
            String filename = String.format("Remittance_Advice_%s_%s.pdf",
                paymentRun.getRunDate().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                paymentRun.getId());

            Attachment attachment = attachmentService.uploadAndLink(
                company,
                filename,
                "application/pdf",
                baos.toByteArray(),
                creator,
                EntityType.PAYMENT_RUN,
                paymentRun.getId()
            );

            log.info("Generated remittance advice PDF: {} ({} bytes)",
                filename, baos.size());

            return attachment;

        } catch (Exception e) {
            log.error("Failed to generate remittance advice PDF", e);
            throw new RuntimeException("Failed to generate remittance advice: " + e.getMessage(), e);
        }
    }

    private void addSupplierRemittance(Document document, Company company, PaymentRun paymentRun,
                                        Contact supplier, List<PaymentRunService.PaymentRunBill> bills)
            throws DocumentException {

        // Calculate totals
        BigDecimal totalAmount = bills.stream()
            .map(PaymentRunService.PaymentRunBill::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Title
        Paragraph title = new Paragraph("REMITTANCE ADVICE", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        // Two-column layout for company and supplier info
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setSpacingAfter(20);

        // Company info (left column)
        PdfPCell companyCell = new PdfPCell();
        companyCell.setBorder(Rectangle.NO_BORDER);
        companyCell.addElement(new Paragraph("From:", BOLD_FONT));
        companyCell.addElement(new Paragraph(company.getName(), NORMAL_FONT));
        headerTable.addCell(companyCell);

        // Supplier info (right column)
        PdfPCell supplierCell = new PdfPCell();
        supplierCell.setBorder(Rectangle.NO_BORDER);
        supplierCell.addElement(new Paragraph("To:", BOLD_FONT));
        supplierCell.addElement(new Paragraph(supplier.getName(), NORMAL_FONT));
        supplierCell.addElement(new Paragraph(supplier.getCode(), SMALL_FONT));
        if (supplier.getFormattedAddress() != null && !supplier.getFormattedAddress().isBlank()) {
            supplierCell.addElement(new Paragraph(supplier.getFormattedAddress(), SMALL_FONT));
        }
        headerTable.addCell(supplierCell);

        document.add(headerTable);

        // Payment details
        PdfPTable paymentDetails = new PdfPTable(2);
        paymentDetails.setWidthPercentage(50);
        paymentDetails.setHorizontalAlignment(Element.ALIGN_LEFT);
        paymentDetails.setSpacingAfter(15);

        addDetailRow(paymentDetails, "Payment Date:", paymentRun.getRunDate().format(dateFormatter));
        addDetailRow(paymentDetails, "Payment Run #:", paymentRun.getId().toString());
        addDetailRow(paymentDetails, "Payment Method:", "Electronic Transfer");

        // Bank account info if available
        if (supplier.getBankAccountNumber() != null && !supplier.getBankAccountNumber().isBlank()) {
            String bankInfo = supplier.getBankName() != null
                ? supplier.getBankName() + " - " + supplier.getBankAccountNumber()
                : supplier.getBankAccountNumber();
            addDetailRow(paymentDetails, "Paid to Account:", bankInfo);
        }

        document.add(paymentDetails);

        // Bills table
        document.add(new Paragraph("Bills Included in This Payment:", HEADING_FONT));
        document.add(Chunk.NEWLINE);

        PdfPTable billsTable = new PdfPTable(5);
        billsTable.setWidthPercentage(100);
        billsTable.setWidths(new float[]{2f, 2f, 2f, 2f, 2f});

        // Table headers
        addTableHeader(billsTable, "Bill Number");
        addTableHeader(billsTable, "Bill Date");
        addTableHeader(billsTable, "Due Date");
        addTableHeader(billsTable, "Reference");
        addTableHeader(billsTable, "Amount Paid");

        // Bill rows
        boolean alternate = false;
        for (PaymentRunService.PaymentRunBill rb : bills) {
            SupplierBill bill = rb.bill();
            Color bgColor = alternate ? ALT_ROW_BG : Color.WHITE;
            alternate = !alternate;

            addTableCell(billsTable, bill.getBillNumber(), bgColor, Element.ALIGN_LEFT);
            addTableCell(billsTable, bill.getBillDate().format(dateFormatter), bgColor, Element.ALIGN_CENTER);
            addTableCell(billsTable, bill.getDueDate().format(dateFormatter), bgColor, Element.ALIGN_CENTER);
            addTableCell(billsTable, bill.getSupplierReference() != null ? bill.getSupplierReference() : "-",
                bgColor, Element.ALIGN_LEFT);
            addTableCell(billsTable, formatCurrency(rb.amount()), bgColor, Element.ALIGN_RIGHT);
        }

        // Total row
        PdfPCell emptyCell = new PdfPCell(new Phrase(""));
        emptyCell.setColspan(4);
        emptyCell.setBorder(Rectangle.TOP);
        emptyCell.setBorderColor(Color.GRAY);
        billsTable.addCell(emptyCell);

        PdfPCell totalCell = new PdfPCell(new Phrase(formatCurrency(totalAmount), BOLD_FONT));
        totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalCell.setBackgroundColor(new Color(230, 247, 230)); // Light green
        totalCell.setBorder(Rectangle.TOP);
        totalCell.setBorderColor(Color.GRAY);
        totalCell.setPadding(8);
        billsTable.addCell(totalCell);

        document.add(billsTable);

        // Payment summary box
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        PdfPTable summaryTable = new PdfPTable(1);
        summaryTable.setWidthPercentage(40);
        summaryTable.setHorizontalAlignment(Element.ALIGN_RIGHT);

        PdfPCell summaryCell = new PdfPCell();
        summaryCell.setBackgroundColor(new Color(230, 247, 230));
        summaryCell.setPadding(15);

        Paragraph summaryTitle = new Paragraph("TOTAL PAYMENT", BOLD_FONT);
        summaryTitle.setAlignment(Element.ALIGN_CENTER);
        summaryCell.addElement(summaryTitle);

        Paragraph summaryAmount = new Paragraph(formatCurrency(totalAmount),
            new Font(Font.HELVETICA, 18, Font.BOLD, new Color(34, 139, 34)));
        summaryAmount.setAlignment(Element.ALIGN_CENTER);
        summaryCell.addElement(summaryAmount);

        summaryTable.addCell(summaryCell);
        document.add(summaryTable);

        // Footer
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        Paragraph footer = new Paragraph(
            "This remittance advice was generated on " +
            LocalDate.now().format(dateFormatter) + ". " +
            "Please contact us if you have any questions regarding this payment.",
            SMALL_FONT
        );
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }

    private void addDetailRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, BOLD_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingBottom(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, NORMAL_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPaddingBottom(5);
        table.addCell(valueCell);
    }

    private void addTableHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_HEADER_FONT));
        cell.setBackgroundColor(HEADER_BG);
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text, Color bgColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_CELL_FONT));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(6);
        cell.setHorizontalAlignment(alignment);
        cell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cell);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return currencyFormat.format(BigDecimal.ZERO);
        return currencyFormat.format(amount);
    }
}
