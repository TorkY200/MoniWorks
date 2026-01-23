package com.example.application.domain;

import java.time.Instant;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents a file attachment stored outside the database. Attachments support source document
 * storage for transactions, invoices, bills, etc. Files are stored externally with SHA-256 checksum
 * for integrity verification.
 */
@Entity
@Table(
    name = "attachment",
    indexes = {
      @Index(name = "idx_attachment_company", columnList = "company_id"),
      @Index(name = "idx_attachment_checksum", columnList = "company_id, checksum_sha256")
    })
public class Attachment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @NotBlank
  @Size(max = 255)
  @Column(name = "filename", nullable = false, length = 255)
  private String filename;

  @NotBlank
  @Size(max = 100)
  @Column(name = "mime_type", nullable = false, length = 100)
  private String mimeType;

  @NotNull
  @Column(name = "size", nullable = false)
  private Long size;

  @NotBlank
  @Size(max = 64)
  @Column(name = "checksum_sha256", nullable = false, length = 64)
  private String checksumSha256;

  @NotBlank
  @Size(max = 500)
  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @NotNull
  @Column(name = "uploaded_at", nullable = false, updatable = false)
  private Instant uploadedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "uploaded_by")
  private User uploadedBy;

  @PrePersist
  protected void onCreate() {
    uploadedAt = Instant.now();
  }

  // Constructors
  public Attachment() {}

  public Attachment(
      Company company,
      String filename,
      String mimeType,
      Long size,
      String checksumSha256,
      String storageKey,
      User uploadedBy) {
    this.company = company;
    this.filename = filename;
    this.mimeType = mimeType;
    this.size = size;
    this.checksumSha256 = checksumSha256;
    this.storageKey = storageKey;
    this.uploadedBy = uploadedBy;
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Company getCompany() {
    return company;
  }

  public void setCompany(Company company) {
    this.company = company;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public Long getSize() {
    return size;
  }

  public void setSize(Long size) {
    this.size = size;
  }

  public String getChecksumSha256() {
    return checksumSha256;
  }

  public void setChecksumSha256(String checksumSha256) {
    this.checksumSha256 = checksumSha256;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public void setStorageKey(String storageKey) {
    this.storageKey = storageKey;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public User getUploadedBy() {
    return uploadedBy;
  }

  public void setUploadedBy(User uploadedBy) {
    this.uploadedBy = uploadedBy;
  }

  /** Check if this is an image file based on mime type. */
  public boolean isImage() {
    return mimeType != null && mimeType.startsWith("image/");
  }

  /** Check if this is a PDF file based on mime type. */
  public boolean isPdf() {
    return "application/pdf".equals(mimeType);
  }

  /** Get a human-readable file size string. */
  public String getFormattedSize() {
    if (size == null) return "0 B";
    if (size < 1024) return size + " B";
    if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
    if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
    return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
  }
}
