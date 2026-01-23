package com.example.application.service;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.domain.*;
import com.example.application.domain.AttachmentLink.EntityType;
import com.example.application.repository.AttachmentLinkRepository;
import com.example.application.repository.AttachmentRepository;

/**
 * Service for managing file attachments. Handles file upload, storage, retrieval, and linking to
 * entities. Files are stored outside the database with SHA-256 checksums for integrity.
 */
@Service
@Transactional
public class AttachmentService {

  private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

  // Allowed MIME types for attachments
  private static final Set<String> ALLOWED_MIME_TYPES =
      Set.of(
          "application/pdf",
          "image/jpeg",
          "image/png",
          "image/gif",
          "image/webp",
          "image/tiff",
          "image/bmp");

  // Maximum file size (10 MB)
  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

  private final AttachmentRepository attachmentRepository;
  private final AttachmentLinkRepository linkRepository;
  private final AuditService auditService;
  private final ObjectProvider<CompanyContextService> companyContextServiceProvider;

  @Value("${moniworks.attachments.storage-path:./data/attachments}")
  private String storagePath;

  public AttachmentService(
      AttachmentRepository attachmentRepository,
      AttachmentLinkRepository linkRepository,
      AuditService auditService,
      ObjectProvider<CompanyContextService> companyContextServiceProvider) {
    this.attachmentRepository = attachmentRepository;
    this.linkRepository = linkRepository;
    this.auditService = auditService;
    this.companyContextServiceProvider = companyContextServiceProvider;
  }

  /**
   * Uploads a file and creates an attachment record. Supports deduplication by checksum - if the
   * same file already exists, returns the existing attachment instead of creating a duplicate.
   *
   * @param company The company context
   * @param filename Original filename
   * @param mimeType MIME type of the file
   * @param content File content as bytes
   * @param uploadedBy User who uploaded the file
   * @return The attachment record (existing or new)
   * @throws IllegalArgumentException if file type not allowed or file too large
   */
  public Attachment uploadFile(
      Company company, String filename, String mimeType, byte[] content, User uploadedBy) {
    // Validate MIME type
    if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
      throw new IllegalArgumentException(
          "File type not allowed: "
              + mimeType
              + ". Allowed types: PDF, JPEG, PNG, GIF, WEBP, TIFF, BMP");
    }

    // Validate file size
    if (content.length > MAX_FILE_SIZE) {
      throw new IllegalArgumentException(
          "File too large. Maximum size is 10 MB. File size: " + formatSize(content.length));
    }

    // Calculate checksum
    String checksum = calculateSha256(content);

    // Check for existing file with same checksum (deduplication)
    Optional<Attachment> existing =
        attachmentRepository.findByCompanyAndChecksumSha256(company, checksum);
    if (existing.isPresent()) {
      log.info(
          "File with same checksum already exists, returning existing attachment: {}",
          existing.get().getId());
      return existing.get();
    }

    // Generate storage key (path within storage directory)
    String storageKey = generateStorageKey(company, filename);

    // Store file to disk
    Path filePath = getStoragePath(storageKey);
    try {
      Files.createDirectories(filePath.getParent());
      Files.write(filePath, content);
      log.info("Stored file: {} ({} bytes)", filePath, content.length);
    } catch (IOException e) {
      log.error("Failed to store file: {}", filePath, e);
      throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
    }

    // Create attachment record
    Attachment attachment =
        new Attachment(
            company, filename, mimeType, (long) content.length, checksum, storageKey, uploadedBy);
    attachment = attachmentRepository.save(attachment);

    // Log audit event
    auditService.logEvent(
        company,
        uploadedBy,
        "ATTACHMENT_UPLOADED",
        "ATTACHMENT",
        attachment.getId(),
        "Uploaded file: " + filename);

    return attachment;
  }

  /**
   * Links an attachment to an entity.
   *
   * @param attachment The attachment to link
   * @param entityType The type of entity (TRANSACTION, INVOICE, etc.)
   * @param entityId The ID of the entity
   * @return The link record
   */
  public AttachmentLink linkToEntity(Attachment attachment, EntityType entityType, Long entityId) {
    // Check if link already exists
    Optional<AttachmentLink> existing =
        linkRepository.findByAttachmentAndEntityTypeAndEntityId(attachment, entityType, entityId);
    if (existing.isPresent()) {
      return existing.get();
    }

    AttachmentLink link = new AttachmentLink(attachment, entityType, entityId);
    return linkRepository.save(link);
  }

  /** Uploads a file and links it to an entity in one operation. */
  public Attachment uploadAndLink(
      Company company,
      String filename,
      String mimeType,
      byte[] content,
      User uploadedBy,
      EntityType entityType,
      Long entityId) {
    Attachment attachment = uploadFile(company, filename, mimeType, content, uploadedBy);
    linkToEntity(attachment, entityType, entityId);
    return attachment;
  }

  /**
   * Retrieves the file content for an attachment.
   *
   * @param attachment The attachment to retrieve
   * @return File content as bytes
   * @throws RuntimeException if file cannot be read or checksum mismatch
   * @throws SecurityException if user does not have access to the attachment's company
   */
  @Transactional(readOnly = true)
  public byte[] getFileContent(Attachment attachment) {
    // Security check: verify the user has access to the company that owns this attachment
    verifyCompanyAccess(attachment);

    Path filePath = getStoragePath(attachment.getStorageKey());

    try {
      byte[] content = Files.readAllBytes(filePath);

      // Verify checksum
      String checksum = calculateSha256(content);
      if (!checksum.equals(attachment.getChecksumSha256())) {
        log.error(
            "Checksum mismatch for attachment {}: expected {}, got {}",
            attachment.getId(),
            attachment.getChecksumSha256(),
            checksum);
        throw new RuntimeException("File integrity check failed - checksum mismatch");
      }

      return content;
    } catch (IOException e) {
      log.error("Failed to read file: {}", filePath, e);
      throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
    }
  }

  /** Finds all attachments linked to an entity. */
  @Transactional(readOnly = true)
  public List<Attachment> findByEntity(EntityType entityType, Long entityId) {
    return linkRepository.findAttachmentsByEntity(entityType, entityId);
  }

  /** Finds all attachments for a company. */
  @Transactional(readOnly = true)
  public List<Attachment> findByCompany(Company company) {
    return attachmentRepository.findByCompanyOrderByUploadedAtDesc(company);
  }

  /** Finds an attachment by ID. */
  @Transactional(readOnly = true)
  public Optional<Attachment> findById(Long id) {
    return attachmentRepository.findById(id);
  }

  /**
   * Downloads a file by attachment ID. Convenience method for retrieving file content without
   * needing the full attachment object.
   *
   * @param attachmentId The attachment ID
   * @return File content as bytes, or null if attachment not found
   * @throws SecurityException if user does not have access to the attachment's company
   */
  @Transactional(readOnly = true)
  public byte[] downloadFile(Long attachmentId) {
    Optional<Attachment> attachment = attachmentRepository.findById(attachmentId);
    if (attachment.isEmpty()) {
      return null;
    }

    // Security check is performed inside getFileContent
    return getFileContent(attachment.get());
  }

  /** Unlinks an attachment from an entity. Does not delete the attachment itself. */
  public void unlinkFromEntity(Attachment attachment, EntityType entityType, Long entityId) {
    linkRepository
        .findByAttachmentAndEntityTypeAndEntityId(attachment, entityType, entityId)
        .ifPresent(linkRepository::delete);
  }

  /** Deletes an attachment and its file. Also removes all links to the attachment. */
  public void deleteAttachment(Attachment attachment, User deletedBy) {
    // Delete links first
    linkRepository.deleteByAttachment(attachment);

    // Delete file from storage
    Path filePath = getStoragePath(attachment.getStorageKey());
    try {
      Files.deleteIfExists(filePath);
      log.info("Deleted file: {}", filePath);
    } catch (IOException e) {
      log.warn("Failed to delete file: {} - {}", filePath, e.getMessage());
    }

    // Log audit event before deletion
    auditService.logEvent(
        attachment.getCompany(),
        deletedBy,
        "ATTACHMENT_DELETED",
        "ATTACHMENT",
        attachment.getId(),
        "Deleted file: " + attachment.getFilename());

    // Delete attachment record
    attachmentRepository.delete(attachment);
  }

  /** Counts attachments linked to an entity. */
  @Transactional(readOnly = true)
  public long countByEntity(EntityType entityType, Long entityId) {
    return linkRepository.countByEntityTypeAndEntityId(entityType, entityId);
  }

  /** Searches attachments by filename. */
  @Transactional(readOnly = true)
  public List<Attachment> searchByFilename(Company company, String filename) {
    return attachmentRepository.searchByFilename(company, filename);
  }

  // Helper methods

  private Path getStoragePath(String storageKey) {
    return Paths.get(storagePath, storageKey);
  }

  private String generateStorageKey(Company company, String filename) {
    // Format: {companyId}/{year}/{month}/{uuid}_{sanitizedFilename}
    LocalDate now = LocalDate.now();
    String uuid = UUID.randomUUID().toString().substring(0, 8);
    String sanitized = sanitizeFilename(filename);

    return String.format(
        "%d/%d/%02d/%s_%s", company.getId(), now.getYear(), now.getMonthValue(), uuid, sanitized);
  }

  private String sanitizeFilename(String filename) {
    // Remove or replace unsafe characters
    return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private String calculateSha256(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content);
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }

  private String formatSize(long size) {
    if (size < 1024) return size + " B";
    if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
    return String.format("%.1f MB", size / (1024.0 * 1024));
  }

  /**
   * Verifies that the current user has access to the company that owns the attachment. Throws a
   * SecurityException if the user does not have access.
   *
   * @param attachment The attachment to verify access for
   * @throws SecurityException if user does not have access to the attachment's company
   */
  private void verifyCompanyAccess(Attachment attachment) {
    CompanyContextService companyContextService = companyContextServiceProvider.getIfAvailable();
    if (companyContextService == null) {
      // If no company context service is available (e.g., in tests or batch jobs), skip the check
      log.debug("No CompanyContextService available, skipping company access verification");
      return;
    }

    Company currentCompany = companyContextService.getCurrentCompany();
    if (currentCompany == null) {
      log.warn("Attempted to access attachment {} without company context", attachment.getId());
      throw new SecurityException("No company context available");
    }

    if (!currentCompany.getId().equals(attachment.getCompany().getId())) {
      log.warn(
          "Attempted to access attachment {} belonging to company {} from company {}",
          attachment.getId(),
          attachment.getCompany().getId(),
          currentCompany.getId());
      throw new SecurityException("Access denied: attachment belongs to different company");
    }
  }

  /** Get the configured storage path (for testing/diagnostics). */
  public String getStoragePath() {
    return storagePath;
  }
}
