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

  public CompanySettings() {
    this.pdfSettings = new PdfSettings();
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
}
