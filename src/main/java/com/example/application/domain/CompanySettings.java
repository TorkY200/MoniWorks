package com.example.application.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Root container for all company-level settings stored in Company.settingsJson. Uses nested objects
 * to organize different setting categories.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompanySettings {

  // PDF generation settings
  private PdfSettings pdfSettings;

  // Tax settings (for future use - cash vs invoice basis)
  private String taxBasis; // CASH, INVOICE

  // Audit event retention policy settings
  private AuditRetentionSettings auditRetention;

  public CompanySettings() {
    this.pdfSettings = new PdfSettings();
    this.auditRetention = new AuditRetentionSettings();
  }

  public PdfSettings getPdfSettings() {
    return pdfSettings;
  }

  public void setPdfSettings(PdfSettings pdfSettings) {
    this.pdfSettings = pdfSettings;
  }

  public String getTaxBasis() {
    return taxBasis;
  }

  public void setTaxBasis(String taxBasis) {
    this.taxBasis = taxBasis;
  }

  /** Gets PDF settings, creating default if null. */
  public PdfSettings getOrCreatePdfSettings() {
    if (pdfSettings == null) {
      pdfSettings = new PdfSettings();
    }
    return pdfSettings;
  }

  public AuditRetentionSettings getAuditRetention() {
    return auditRetention;
  }

  public void setAuditRetention(AuditRetentionSettings auditRetention) {
    this.auditRetention = auditRetention;
  }

  /** Gets audit retention settings, creating default if null. */
  public AuditRetentionSettings getOrCreateAuditRetention() {
    if (auditRetention == null) {
      auditRetention = new AuditRetentionSettings();
    }
    return auditRetention;
  }
}
