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
 * Service for generating sales invoice PDFs. Creates professional invoice documents with company
 * branding, line items, tax breakdown, and payment terms. Supports customization via PdfSettings
 * (logo, colors, footer).
 */
@Service
public class InvoicePdfService {

  private static final Logger log = LoggerFactory.getLogger(InvoicePdfService.class);

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

  public InvoicePdfService(AttachmentService attachmentService) {
    this.attachmentService = attachmentService;
    this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "NZ"));
  }

  /**
   * Generates a professional PDF for a sales invoice using default settings.
   *
   * @param invoice The invoice to generate PDF for
   * @return byte array containing the PDF content
   */
  public byte[] generateInvoicePdf(SalesInvoice invoice) {
    return generateInvoicePdf(invoice, new PdfSettings());
  }

  /**
   * Generates a professional PDF for a sales invoice with custom settings.
   *
   * @param invoice The invoice to generate PDF for
   * @param settings PDF customization settings
   * @return byte array containing the PDF content
   */
  public byte[] generateInvoicePdf(SalesInvoice invoice, PdfSettings settings) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Rectangle pageSize = getPageSize(settings);
      Document document = new Document(pageSize, 50, 50, 50, 50);
      PdfWriter.getInstance(document, baos);
      document.open();

      addInvoiceContent(document, invoice, settings);

      document.close();

      log.info(
          "Generated invoice PDF for invoice {}: {} bytes",
          invoice.getInvoiceNumber(),
          baos.size());

      return baos.toByteArray();

    } catch (Exception e) {
      log.error("Failed to generate invoice PDF for {}", invoice.getInvoiceNumber(), e);
      throw new RuntimeException("Failed to generate invoice PDF: " + e.getMessage(), e);
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

  private void addInvoiceContent(Document document, SalesInvoice invoice, PdfSettings settings)
      throws DocumentException, IOException {
    Company company = invoice.getCompany();
    Contact customer = invoice.getContact();

    Color primaryColor = parseColor(settings.getEffectivePrimaryColor(), new Color(52, 73, 94));
    Color accentColor = parseColor(settings.getEffectiveAccentColor(), new Color(41, 128, 185));

    // Header with INVOICE title and company info
    addHeader(document, company, invoice, settings, primaryColor);

    // Bill To section
    addBillToSection(document, customer, accentColor);

    // Invoice details (number, dates)
    addInvoiceDetails(document, invoice);

    // Line items table
    addLineItemsTable(document, invoice, primaryColor);

    // Totals section
    addTotalsSection(document, invoice);

    // Payment information
    addPaymentInfo(document, company, invoice, settings, accentColor);

    // Footer with notes
    addFooter(document, invoice, settings);
  }

  private void addHeader(
      Document document,
      Company company,
      SalesInvoice invoice,
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

    // Tax Invoice label
    Paragraph taxInvoiceLabel = new Paragraph();
    taxInvoiceLabel.setSpacingBefore(10);
    String docType = invoice.isCreditNote() ? "Credit Note" : "Tax Invoice";
    taxInvoiceLabel.add(new Chunk(docType, SMALL_BOLD_FONT));
    companyCell.addElement(taxInvoiceLabel);

    headerTable.addCell(companyCell);

    // Right: INVOICE title and status
    PdfPCell invoiceCell = new PdfPCell();
    invoiceCell.setBorder(Rectangle.NO_BORDER);
    invoiceCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

    Font titleFont = new Font(Font.HELVETICA, 24, Font.BOLD, primaryColor);
    String titleText = invoice.isCreditNote() ? "CREDIT NOTE" : "INVOICE";
    Paragraph invoiceTitle = new Paragraph(titleText, titleFont);
    invoiceTitle.setAlignment(Element.ALIGN_RIGHT);
    invoiceCell.addElement(invoiceTitle);

    // Status badge
    String statusText = invoice.getStatus().name();
    Color statusColor =
        switch (invoice.getStatus()) {
          case ISSUED ->
              invoice.isPaid()
                  ? SUCCESS_GREEN
                  : invoice.isOverdue()
                      ? WARNING_RED
                      : parseColor(settings.getEffectiveAccentColor(), new Color(41, 128, 185));
          case DRAFT -> Color.GRAY;
          case VOID -> WARNING_RED;
        };

    if (invoice.isIssued() && invoice.isPaid()) {
      statusText = "PAID";
    } else if (invoice.isIssued() && invoice.isOverdue()) {
      statusText = "OVERDUE";
    }

    Font statusFont = new Font(Font.HELVETICA, 11, Font.BOLD, statusColor);
    Paragraph status = new Paragraph(statusText, statusFont);
    status.setAlignment(Element.ALIGN_RIGHT);
    invoiceCell.addElement(status);

    headerTable.addCell(invoiceCell);

    document.add(headerTable);
  }

  private void addBillToSection(Document document, Contact customer, Color accentColor)
      throws DocumentException {
    PdfPTable table = new PdfPTable(1);
    table.setWidthPercentage(50);
    table.setHorizontalAlignment(Element.ALIGN_LEFT);
    table.setSpacingAfter(15);

    PdfPCell cell = new PdfPCell();
    cell.setBackgroundColor(LIGHT_BLUE_BG);
    cell.setPadding(10);
    cell.setBorderColor(accentColor);

    cell.addElement(new Paragraph("BILL TO", SMALL_BOLD_FONT));

    Paragraph customerName = new Paragraph(customer.getName(), BOLD_FONT);
    cell.addElement(customerName);

    if (customer.getCode() != null && !customer.getCode().isBlank()) {
      cell.addElement(new Paragraph("Account: " + customer.getCode(), SMALL_FONT));
    }

    if (customer.getFormattedAddress() != null && !customer.getFormattedAddress().isBlank()) {
      Paragraph address = new Paragraph(customer.getFormattedAddress(), SMALL_FONT);
      address.setSpacingBefore(5);
      cell.addElement(address);
    }

    if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
      cell.addElement(new Paragraph(customer.getEmail(), SMALL_FONT));
    }

    table.addCell(cell);
    document.add(table);
  }

  private void addInvoiceDetails(Document document, SalesInvoice invoice) throws DocumentException {
    PdfPTable table = new PdfPTable(4);
    table.setWidthPercentage(100);
    table.setWidths(new float[] {25, 25, 25, 25});
    table.setSpacingAfter(20);

    // Invoice Number
    String numberLabel = invoice.isCreditNote() ? "Credit Note #" : "Invoice Number";
    addDetailBox(table, numberLabel, invoice.getInvoiceNumber());

    // Issue Date
    addDetailBox(table, "Issue Date", invoice.getIssueDate().format(shortDateFormatter));

    // Due Date (show original invoice for credit notes)
    if (invoice.isCreditNote() && invoice.getOriginalInvoice() != null) {
      addDetailBox(table, "Original Invoice", invoice.getOriginalInvoice().getInvoiceNumber());
    } else {
      addDetailBox(table, "Due Date", invoice.getDueDate().format(shortDateFormatter));
    }

    // Reference (if any)
    String reference =
        invoice.getReference() != null && !invoice.getReference().isBlank()
            ? invoice.getReference()
            : "-";
    addDetailBox(table, "Reference", reference);

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

  private void addLineItemsTable(Document document, SalesInvoice invoice, Color primaryColor)
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
    for (SalesInvoiceLine line : invoice.getLines()) {
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

  private void addTotalsSection(Document document, SalesInvoice invoice) throws DocumentException {
    PdfPTable table = new PdfPTable(2);
    table.setWidthPercentage(45);
    table.setHorizontalAlignment(Element.ALIGN_RIGHT);
    table.setWidths(new float[] {60, 40});
    table.setSpacingAfter(20);

    // Subtotal
    addTotalRow(table, "Subtotal", invoice.getSubtotal(), false);

    // Tax breakdown by tax code
    Map<String, BigDecimal> taxByCode = new LinkedHashMap<>();
    for (SalesInvoiceLine line : invoice.getLines()) {
      if (line.getTaxCode() != null
          && line.getTaxAmount() != null
          && line.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
        String code = line.getTaxCode();
        taxByCode.merge(code, line.getTaxAmount(), BigDecimal::add);
      }
    }

    if (taxByCode.isEmpty() && invoice.getTaxTotal().compareTo(BigDecimal.ZERO) > 0) {
      addTotalRow(table, "GST", invoice.getTaxTotal(), false);
    } else {
      for (Map.Entry<String, BigDecimal> entry : taxByCode.entrySet()) {
        addTotalRow(table, entry.getKey(), entry.getValue(), false);
      }
    }

    // Total
    addTotalRow(table, "TOTAL", invoice.getTotal(), true);

    // Amount paid (if any) - not applicable for credit notes
    if (!invoice.isCreditNote()
        && invoice.getAmountPaid() != null
        && invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
      addTotalRow(table, "Amount Paid", invoice.getAmountPaid().negate(), false);
      addTotalRow(table, "BALANCE DUE", invoice.getBalance(), true);
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

  private void addPaymentInfo(
      Document document,
      Company company,
      SalesInvoice invoice,
      PdfSettings settings,
      Color accentColor)
      throws DocumentException {
    if (invoice.isPaid()) {
      // Show "PAID" stamp
      Paragraph paid =
          new Paragraph("PAID IN FULL", new Font(Font.HELVETICA, 14, Font.BOLD, SUCCESS_GREEN));
      paid.setAlignment(Element.ALIGN_CENTER);
      paid.setSpacingBefore(10);
      paid.setSpacingAfter(20);
      document.add(paid);
    } else if (!invoice.isCreditNote()) {
      // Payment instructions (not for credit notes)
      PdfPTable table = new PdfPTable(1);
      table.setWidthPercentage(100);
      table.setSpacingBefore(10);
      table.setSpacingAfter(20);

      PdfPCell cell = new PdfPCell();
      cell.setBackgroundColor(LIGHT_BLUE_BG);
      cell.setPadding(15);
      cell.setBorderColor(accentColor);

      cell.addElement(new Paragraph("Payment Information", HEADING_FONT));

      Paragraph terms = new Paragraph();
      terms.setSpacingBefore(10);
      terms.add(new Chunk("Payment Due: ", BOLD_FONT));
      terms.add(new Chunk(invoice.getDueDate().format(dateFormatter), NORMAL_FONT));
      cell.addElement(terms);

      // Bank details from settings
      if (settings.hasBankDetails()) {
        Paragraph bankDetails = new Paragraph();
        bankDetails.setSpacingBefore(10);

        if (settings.getBankName() != null && !settings.getBankName().isBlank()) {
          bankDetails.add(new Chunk("Bank: ", BOLD_FONT));
          bankDetails.add(new Chunk(settings.getBankName() + "\n", NORMAL_FONT));
        }
        if (settings.getBankAccountName() != null && !settings.getBankAccountName().isBlank()) {
          bankDetails.add(new Chunk("Account Name: ", BOLD_FONT));
          bankDetails.add(new Chunk(settings.getBankAccountName() + "\n", NORMAL_FONT));
        }
        bankDetails.add(new Chunk("Account Number: ", BOLD_FONT));
        bankDetails.add(new Chunk(settings.getBankAccountNumber(), NORMAL_FONT));
        cell.addElement(bankDetails);
      }

      Paragraph bankInfo = new Paragraph();
      bankInfo.setSpacingBefore(5);
      bankInfo.add(new Chunk("Please quote invoice number ", SMALL_FONT));
      bankInfo.add(new Chunk(invoice.getInvoiceNumber(), SMALL_BOLD_FONT));
      bankInfo.add(new Chunk(" with your payment.", SMALL_FONT));
      cell.addElement(bankInfo);

      table.addCell(cell);
      document.add(table);
    }
  }

  private void addFooter(Document document, SalesInvoice invoice, PdfSettings settings)
      throws DocumentException {
    // Notes section
    if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
      Paragraph notesTitle = new Paragraph("Notes:", SMALL_BOLD_FONT);
      notesTitle.setSpacingBefore(10);
      document.add(notesTitle);

      Paragraph notes = new Paragraph(invoice.getNotes(), SMALL_FONT);
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
