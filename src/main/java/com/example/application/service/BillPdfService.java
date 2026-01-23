package com.example.application.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.application.domain.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

/**
 * Service for generating supplier bill PDFs. Creates professional bill summary documents with
 * company branding, line items, tax breakdown, and payment status. Useful for internal records and
 * audit purposes. Supports customization via PdfSettings (logo, colors, footer).
 */
@Service
public class BillPdfService {

  private static final Logger log = LoggerFactory.getLogger(BillPdfService.class);

  // Default fonts
  private static final Font HEADING_FONT = new Font(Font.HELVETICA, 12, Font.BOLD);
  private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);
  private static final Font BOLD_FONT = new Font(Font.HELVETICA, 10, Font.BOLD);
  private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL);
  private static final Font SMALL_BOLD_FONT = new Font(Font.HELVETICA, 8, Font.BOLD);
  private static final Font TABLE_CELL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL);
  private static final Font LARGE_BOLD_FONT = new Font(Font.HELVETICA, 14, Font.BOLD);

  // Static colors (non-customizable)
  private static final Color ALT_ROW_BG = new Color(245, 247, 249); // Light gray
  private static final Color LIGHT_BLUE_BG = new Color(235, 245, 251); // Light blue
  private static final Color SUCCESS_GREEN = new Color(39, 174, 96); // Green for paid
  private static final Color WARNING_RED = new Color(231, 76, 60); // Red for overdue

  private final NumberFormat currencyFormat;
  private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy");
  private final DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final AttachmentService attachmentService;

  public BillPdfService(AttachmentService attachmentService) {
    this.attachmentService = attachmentService;
    this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "NZ"));
  }

  /**
   * Generates a professional PDF for a supplier bill using default settings.
   *
   * @param bill The bill to generate PDF for
   * @return byte array containing the PDF content
   */
  public byte[] generateBillPdf(SupplierBill bill) {
    return generateBillPdf(bill, new PdfSettings());
  }

  /**
   * Generates a professional PDF for a supplier bill with custom settings.
   *
   * @param bill The bill to generate PDF for
   * @param settings PDF customization settings
   * @return byte array containing the PDF content
   */
  public byte[] generateBillPdf(SupplierBill bill, PdfSettings settings) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Rectangle pageSize = getPageSize(settings);
      Document document = new Document(pageSize, 50, 50, 50, 50);
      PdfWriter.getInstance(document, baos);
      document.open();

      addBillContent(document, bill, settings);

      document.close();

      log.info("Generated bill PDF for bill {}: {} bytes", bill.getBillNumber(), baos.size());

      return baos.toByteArray();

    } catch (Exception e) {
      log.error("Failed to generate bill PDF for {}", bill.getBillNumber(), e);
      throw new RuntimeException("Failed to generate bill PDF: " + e.getMessage(), e);
    }
  }

  private Rectangle getPageSize(PdfSettings settings) {
    String paperSize = settings.getEffectivePaperSize();
    return switch (paperSize.toUpperCase()) {
      case "LETTER" -> PageSize.LETTER;
      case "LEGAL" -> PageSize.LEGAL;
      default -> PageSize.A4;
    };
  }

  private Color parseColor(String hex, Color defaultColor) {
    if (hex == null || hex.isBlank()) {
      return defaultColor;
    }
    try {
      return Color.decode(hex);
    } catch (NumberFormatException e) {
      log.warn("Invalid color hex: {}, using default", hex);
      return defaultColor;
    }
  }

  private void addBillContent(Document document, SupplierBill bill, PdfSettings settings)
      throws DocumentException, IOException {
    Company company = bill.getCompany();
    Contact supplier = bill.getContact();

    Color primaryColor = parseColor(settings.getEffectivePrimaryColor(), new Color(52, 73, 94));
    Color accentColor = parseColor(settings.getEffectiveAccentColor(), new Color(41, 128, 185));

    // Header with BILL title and company info
    addHeader(document, company, bill, settings, primaryColor);

    // Supplier info section
    addSupplierSection(document, supplier, accentColor);

    // Bill details (number, dates)
    addBillDetails(document, bill);

    // Line items table
    addLineItemsTable(document, bill, primaryColor);

    // Totals section
    addTotalsSection(document, bill);

    // Payment status
    addPaymentStatus(document, bill, accentColor);

    // Footer with notes
    addFooter(document, bill, settings);
  }

  private void addHeader(
      Document document,
      Company company,
      SupplierBill bill,
      PdfSettings settings,
      Color primaryColor)
      throws DocumentException, IOException {
    PdfPTable headerTable = new PdfPTable(2);
    headerTable.setWidthPercentage(100);
    headerTable.setWidths(new float[] {60, 40});
    headerTable.setSpacingAfter(20);

    // Left: Company info (and logo if available)
    PdfPCell companyCell = new PdfPCell();
    companyCell.setBorder(Rectangle.NO_BORDER);

    // Add logo if configured
    if (settings.hasLogo()) {
      try {
        byte[] logoBytes = attachmentService.downloadFile(settings.getLogoAttachmentId());
        if (logoBytes != null && logoBytes.length > 0) {
          Image logo = Image.getInstance(logoBytes);
          // Scale logo to reasonable size (max 150x60)
          logo.scaleToFit(150, 60);
          companyCell.addElement(logo);
          companyCell.addElement(Chunk.NEWLINE);
        }
      } catch (Exception e) {
        log.warn(
            "Failed to load logo attachment {}: {}",
            settings.getLogoAttachmentId(),
            e.getMessage());
      }
    }

    Paragraph companyName = new Paragraph(company.getName(), LARGE_BOLD_FONT);
    companyCell.addElement(companyName);

    // Company details from settings
    if (settings.hasCompanyAddress()) {
      Paragraph address = new Paragraph(settings.getCompanyAddress(), SMALL_FONT);
      companyCell.addElement(address);
    }

    StringBuilder contactLine = new StringBuilder();
    if (settings.hasCompanyPhone()) {
      contactLine.append("Ph: ").append(settings.getCompanyPhone());
    }
    if (settings.hasCompanyEmail()) {
      if (contactLine.length() > 0) contactLine.append(" | ");
      contactLine.append(settings.getCompanyEmail());
    }
    if (contactLine.length() > 0) {
      companyCell.addElement(new Paragraph(contactLine.toString(), SMALL_FONT));
    }

    if (settings.hasCompanyWebsite()) {
      companyCell.addElement(new Paragraph(settings.getCompanyWebsite(), SMALL_FONT));
    }

    if (settings.hasTaxId()) {
      Paragraph taxId = new Paragraph("GST #: " + settings.getTaxId(), SMALL_BOLD_FONT);
      taxId.setSpacingBefore(5);
      companyCell.addElement(taxId);
    }

    // Document type label
    Paragraph docTypeLabel = new Paragraph();
    docTypeLabel.setSpacingBefore(10);
    String docType = bill.isDebitNote() ? "Debit Note Record" : "Supplier Bill Record";
    docTypeLabel.add(new Chunk(docType, SMALL_BOLD_FONT));
    companyCell.addElement(docTypeLabel);

    headerTable.addCell(companyCell);

    // Right: BILL title and status
    PdfPCell billCell = new PdfPCell();
    billCell.setBorder(Rectangle.NO_BORDER);
    billCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

    Font titleFont = new Font(Font.HELVETICA, 24, Font.BOLD, primaryColor);
    String titleText = bill.isDebitNote() ? "DEBIT NOTE" : "SUPPLIER BILL";
    Paragraph billTitle = new Paragraph(titleText, titleFont);
    billTitle.setAlignment(Element.ALIGN_RIGHT);
    billCell.addElement(billTitle);

    // Status badge
    String statusText = bill.getStatus().name();
    Color statusColor =
        switch (bill.getStatus()) {
          case POSTED ->
              bill.isPaid()
                  ? SUCCESS_GREEN
                  : bill.isOverdue()
                      ? WARNING_RED
                      : parseColor(settings.getEffectiveAccentColor(), new Color(41, 128, 185));
          case DRAFT -> Color.GRAY;
          case VOID -> WARNING_RED;
        };

    if (bill.isPosted() && bill.isPaid()) {
      statusText = "PAID";
    } else if (bill.isPosted() && bill.isOverdue()) {
      statusText = "OVERDUE";
    }

    Font statusFont = new Font(Font.HELVETICA, 11, Font.BOLD, statusColor);
    Paragraph status = new Paragraph(statusText, statusFont);
    status.setAlignment(Element.ALIGN_RIGHT);
    billCell.addElement(status);

    headerTable.addCell(billCell);

    document.add(headerTable);
  }

  private void addSupplierSection(Document document, Contact supplier, Color accentColor)
      throws DocumentException {
    PdfPTable table = new PdfPTable(1);
    table.setWidthPercentage(50);
    table.setHorizontalAlignment(Element.ALIGN_LEFT);
    table.setSpacingAfter(15);

    PdfPCell cell = new PdfPCell();
    cell.setBackgroundColor(LIGHT_BLUE_BG);
    cell.setPadding(10);
    cell.setBorderColor(accentColor);

    cell.addElement(new Paragraph("FROM SUPPLIER", SMALL_BOLD_FONT));

    Paragraph supplierName = new Paragraph(supplier.getName(), BOLD_FONT);
    cell.addElement(supplierName);

    if (supplier.getCode() != null && !supplier.getCode().isBlank()) {
      cell.addElement(new Paragraph("Account: " + supplier.getCode(), SMALL_FONT));
    }

    if (supplier.getFormattedAddress() != null && !supplier.getFormattedAddress().isBlank()) {
      Paragraph address = new Paragraph(supplier.getFormattedAddress(), SMALL_FONT);
      address.setSpacingBefore(5);
      cell.addElement(address);
    }

    if (supplier.getEmail() != null && !supplier.getEmail().isBlank()) {
      cell.addElement(new Paragraph(supplier.getEmail(), SMALL_FONT));
    }

    table.addCell(cell);
    document.add(table);
  }

  private void addBillDetails(Document document, SupplierBill bill) throws DocumentException {
    PdfPTable table = new PdfPTable(4);
    table.setWidthPercentage(100);
    table.setWidths(new float[] {25, 25, 25, 25});
    table.setSpacingAfter(20);

    // Bill Number
    String numberLabel = bill.isDebitNote() ? "Debit Note #" : "Bill Number";
    addDetailBox(table, numberLabel, bill.getBillNumber());

    // Bill Date
    addDetailBox(table, "Bill Date", bill.getBillDate().format(shortDateFormatter));

    // Due Date (show original bill for debit notes)
    if (bill.isDebitNote() && bill.getOriginalBill() != null) {
      addDetailBox(table, "Original Bill", bill.getOriginalBill().getBillNumber());
    } else {
      addDetailBox(table, "Due Date", bill.getDueDate().format(shortDateFormatter));
    }

    // Supplier Reference (if any)
    String reference =
        bill.getSupplierReference() != null && !bill.getSupplierReference().isBlank()
            ? bill.getSupplierReference()
            : "-";
    addDetailBox(table, "Supplier Ref", reference);

    document.add(table);
  }

  private void addDetailBox(PdfPTable table, String label, String value) {
    PdfPCell cell = new PdfPCell();
    cell.setBorderColor(Color.LIGHT_GRAY);
    cell.setPadding(8);

    Paragraph labelPara = new Paragraph(label, SMALL_FONT);
    labelPara.setSpacingAfter(3);
    cell.addElement(labelPara);

    Paragraph valuePara = new Paragraph(value, BOLD_FONT);
    cell.addElement(valuePara);

    table.addCell(cell);
  }

  private void addLineItemsTable(Document document, SupplierBill bill, Color primaryColor)
      throws DocumentException {
    PdfPTable table = new PdfPTable(6);
    table.setWidthPercentage(100);
    table.setWidths(new float[] {8, 35, 12, 15, 12, 18});
    table.setSpacingAfter(10);

    Font tableHeaderFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);

    // Headers
    addTableHeader(table, "#", primaryColor, tableHeaderFont);
    addTableHeader(table, "Description", primaryColor, tableHeaderFont);
    addTableHeader(table, "Qty", primaryColor, tableHeaderFont);
    addTableHeader(table, "Unit Price", primaryColor, tableHeaderFont);
    addTableHeader(table, "Tax", primaryColor, tableHeaderFont);
    addTableHeader(table, "Amount", primaryColor, tableHeaderFont);

    // Line items
    boolean alternate = false;
    int lineNum = 1;
    for (SupplierBillLine line : bill.getLines()) {
      Color bgColor = alternate ? ALT_ROW_BG : Color.WHITE;
      alternate = !alternate;

      addTableCell(table, String.valueOf(lineNum++), bgColor, Element.ALIGN_CENTER);

      String description = line.getDescription() != null ? line.getDescription() : "";
      if (line.getProduct() != null && (description.isBlank())) {
        description = line.getProduct().getName();
      }
      addTableCell(table, description, bgColor, Element.ALIGN_LEFT);

      addTableCell(table, formatQuantity(line.getQuantity()), bgColor, Element.ALIGN_CENTER);
      addTableCell(table, formatCurrency(line.getUnitPrice()), bgColor, Element.ALIGN_RIGHT);

      String taxInfo = line.getTaxCode() != null ? line.getTaxCode() : "-";
      addTableCell(table, taxInfo, bgColor, Element.ALIGN_CENTER);

      addTableCell(table, formatCurrency(line.getGrossTotal()), bgColor, Element.ALIGN_RIGHT);
    }

    document.add(table);
  }

  private void addTotalsSection(Document document, SupplierBill bill) throws DocumentException {
    PdfPTable table = new PdfPTable(2);
    table.setWidthPercentage(45);
    table.setHorizontalAlignment(Element.ALIGN_RIGHT);
    table.setWidths(new float[] {60, 40});
    table.setSpacingAfter(20);

    // Subtotal
    addTotalRow(table, "Subtotal", bill.getSubtotal(), false);

    // Tax breakdown by tax code
    Map<String, BigDecimal> taxByCode = new LinkedHashMap<>();
    for (SupplierBillLine line : bill.getLines()) {
      if (line.getTaxCode() != null
          && line.getTaxAmount() != null
          && line.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
        String code = line.getTaxCode();
        taxByCode.merge(code, line.getTaxAmount(), BigDecimal::add);
      }
    }

    if (taxByCode.isEmpty() && bill.getTaxTotal().compareTo(BigDecimal.ZERO) > 0) {
      addTotalRow(table, "GST", bill.getTaxTotal(), false);
    } else {
      for (Map.Entry<String, BigDecimal> entry : taxByCode.entrySet()) {
        addTotalRow(table, entry.getKey(), entry.getValue(), false);
      }
    }

    // Total
    addTotalRow(table, "TOTAL", bill.getTotal(), true);

    // Amount paid (if any) - not applicable for debit notes
    if (!bill.isDebitNote()
        && bill.getAmountPaid() != null
        && bill.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
      addTotalRow(table, "Amount Paid", bill.getAmountPaid().negate(), false);
      addTotalRow(table, "BALANCE DUE", bill.getBalance(), true);
    }

    document.add(table);
  }

  private void addTotalRow(PdfPTable table, String label, BigDecimal amount, boolean isTotal) {
    Font labelFont = isTotal ? BOLD_FONT : NORMAL_FONT;
    Font amountFont = isTotal ? BOLD_FONT : NORMAL_FONT;
    Color bgColor = isTotal ? LIGHT_BLUE_BG : Color.WHITE;

    PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
    labelCell.setBorder(Rectangle.TOP);
    labelCell.setBorderColor(Color.LIGHT_GRAY);
    labelCell.setBackgroundColor(bgColor);
    labelCell.setPadding(8);
    labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    table.addCell(labelCell);

    PdfPCell amountCell = new PdfPCell(new Phrase(formatCurrency(amount), amountFont));
    amountCell.setBorder(Rectangle.TOP);
    amountCell.setBorderColor(Color.LIGHT_GRAY);
    amountCell.setBackgroundColor(bgColor);
    amountCell.setPadding(8);
    amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    table.addCell(amountCell);
  }

  private void addPaymentStatus(Document document, SupplierBill bill, Color accentColor)
      throws DocumentException {
    if (bill.isPaid()) {
      // Show "PAID" stamp
      Paragraph paid =
          new Paragraph("PAID IN FULL", new Font(Font.HELVETICA, 14, Font.BOLD, SUCCESS_GREEN));
      paid.setAlignment(Element.ALIGN_CENTER);
      paid.setSpacingBefore(10);
      paid.setSpacingAfter(20);
      document.add(paid);
    } else if (!bill.isDebitNote() && bill.isPosted()) {
      // Payment status for unpaid bills
      PdfPTable table = new PdfPTable(1);
      table.setWidthPercentage(100);
      table.setSpacingBefore(10);
      table.setSpacingAfter(20);

      PdfPCell cell = new PdfPCell();
      cell.setBackgroundColor(LIGHT_BLUE_BG);
      cell.setPadding(15);
      cell.setBorderColor(accentColor);

      cell.addElement(new Paragraph("Payment Status", HEADING_FONT));

      Paragraph terms = new Paragraph();
      terms.setSpacingBefore(10);
      terms.add(new Chunk("Payment Due: ", BOLD_FONT));
      terms.add(new Chunk(bill.getDueDate().format(dateFormatter), NORMAL_FONT));
      cell.addElement(terms);

      if (bill.isOverdue()) {
        Paragraph overdue = new Paragraph();
        overdue.setSpacingBefore(5);
        overdue.add(
            new Chunk(
                "This bill is OVERDUE", new Font(Font.HELVETICA, 10, Font.BOLD, WARNING_RED)));
        cell.addElement(overdue);
      }

      if (bill.getBalance().compareTo(BigDecimal.ZERO) > 0) {
        Paragraph balance = new Paragraph();
        balance.setSpacingBefore(5);
        balance.add(new Chunk("Amount Outstanding: ", BOLD_FONT));
        balance.add(new Chunk(formatCurrency(bill.getBalance()), NORMAL_FONT));
        cell.addElement(balance);
      }

      table.addCell(cell);
      document.add(table);
    }
  }

  private void addFooter(Document document, SupplierBill bill, PdfSettings settings)
      throws DocumentException {
    // Notes section
    if (bill.getNotes() != null && !bill.getNotes().isBlank()) {
      Paragraph notesTitle = new Paragraph("Notes:", SMALL_BOLD_FONT);
      notesTitle.setSpacingBefore(10);
      document.add(notesTitle);

      Paragraph notes = new Paragraph(bill.getNotes(), SMALL_FONT);
      document.add(notes);
    }

    // Footer line
    document.add(Chunk.NEWLINE);

    String footerText = "Generated on " + LocalDate.now().format(dateFormatter);
    if (settings.hasFooterText()) {
      footerText += " | " + settings.getEffectiveFooterText();
    } else {
      footerText += " | " + PdfSettings.DEFAULT_FOOTER_TEXT;
    }

    Paragraph footer = new Paragraph(footerText, SMALL_FONT);
    footer.setAlignment(Element.ALIGN_CENTER);
    footer.setSpacingBefore(20);
    document.add(footer);
  }

  private void addTableHeader(PdfPTable table, String text, Color bgColor, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setBackgroundColor(bgColor);
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

  private String formatQuantity(BigDecimal qty) {
    if (qty == null) return "1";
    // Remove trailing zeros
    return qty.stripTrailingZeros().toPlainString();
  }
}
