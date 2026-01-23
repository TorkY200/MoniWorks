package com.example.application.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * PDF template settings stored in Company.settingsJson.
 * Configures branding, layout, and content for generated PDFs
 * (invoices, statements, remittance advices, reports).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PdfSettings {

    // Logo attachment ID (references Attachment entity)
    private Long logoAttachmentId;

    // Company details shown on PDFs
    private String companyAddress;
    private String companyPhone;
    private String companyEmail;
    private String companyWebsite;
    private String taxId;  // GST number, VAT ID, etc.

    // Bank details for payment instructions
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountName;

    // Customizable footer text
    private String footerText;

    // Paper size: A4, LETTER
    private String paperSize;

    // Theme colors (hex format: #RRGGBB)
    private String primaryColor;
    private String accentColor;

    // Default values
    public static final String DEFAULT_PAPER_SIZE = "A4";
    public static final String DEFAULT_PRIMARY_COLOR = "#34495e";  // Dark blue-gray
    public static final String DEFAULT_ACCENT_COLOR = "#2980b9";   // Blue
    public static final String DEFAULT_FOOTER_TEXT = "Thank you for your business";

    public PdfSettings() {
        // Defaults
        this.paperSize = DEFAULT_PAPER_SIZE;
        this.primaryColor = DEFAULT_PRIMARY_COLOR;
        this.accentColor = DEFAULT_ACCENT_COLOR;
        this.footerText = DEFAULT_FOOTER_TEXT;
    }

    // Getters and Setters
    public Long getLogoAttachmentId() {
        return logoAttachmentId;
    }

    public void setLogoAttachmentId(Long logoAttachmentId) {
        this.logoAttachmentId = logoAttachmentId;
    }

    public String getCompanyAddress() {
        return companyAddress;
    }

    public void setCompanyAddress(String companyAddress) {
        this.companyAddress = companyAddress;
    }

    public String getCompanyPhone() {
        return companyPhone;
    }

    public void setCompanyPhone(String companyPhone) {
        this.companyPhone = companyPhone;
    }

    public String getCompanyEmail() {
        return companyEmail;
    }

    public void setCompanyEmail(String companyEmail) {
        this.companyEmail = companyEmail;
    }

    public String getCompanyWebsite() {
        return companyWebsite;
    }

    public void setCompanyWebsite(String companyWebsite) {
        this.companyWebsite = companyWebsite;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public void setBankAccountNumber(String bankAccountNumber) {
        this.bankAccountNumber = bankAccountNumber;
    }

    public String getBankAccountName() {
        return bankAccountName;
    }

    public void setBankAccountName(String bankAccountName) {
        this.bankAccountName = bankAccountName;
    }

    public String getFooterText() {
        return footerText;
    }

    public void setFooterText(String footerText) {
        this.footerText = footerText;
    }

    public String getPaperSize() {
        return paperSize;
    }

    public void setPaperSize(String paperSize) {
        this.paperSize = paperSize;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(String accentColor) {
        this.accentColor = accentColor;
    }

    // Helper methods
    public boolean hasLogo() {
        return logoAttachmentId != null;
    }

    public boolean hasCompanyAddress() {
        return companyAddress != null && !companyAddress.isBlank();
    }

    public boolean hasCompanyPhone() {
        return companyPhone != null && !companyPhone.isBlank();
    }

    public boolean hasCompanyEmail() {
        return companyEmail != null && !companyEmail.isBlank();
    }

    public boolean hasCompanyWebsite() {
        return companyWebsite != null && !companyWebsite.isBlank();
    }

    public boolean hasTaxId() {
        return taxId != null && !taxId.isBlank();
    }

    public boolean hasBankDetails() {
        return bankAccountNumber != null && !bankAccountNumber.isBlank();
    }

    public boolean hasFooterText() {
        return footerText != null && !footerText.isBlank();
    }

    /**
     * Gets effective footer text with fallback to default.
     */
    public String getEffectiveFooterText() {
        return hasFooterText() ? footerText : DEFAULT_FOOTER_TEXT;
    }

    /**
     * Gets effective paper size with fallback to A4.
     */
    public String getEffectivePaperSize() {
        return paperSize != null && !paperSize.isBlank() ? paperSize : DEFAULT_PAPER_SIZE;
    }

    /**
     * Gets effective primary color with fallback.
     */
    public String getEffectivePrimaryColor() {
        return primaryColor != null && !primaryColor.isBlank() ? primaryColor : DEFAULT_PRIMARY_COLOR;
    }

    /**
     * Gets effective accent color with fallback.
     */
    public String getEffectiveAccentColor() {
        return accentColor != null && !accentColor.isBlank() ? accentColor : DEFAULT_ACCENT_COLOR;
    }
}
