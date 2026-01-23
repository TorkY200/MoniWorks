package com.example.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.application.domain.Attachment;
import com.example.application.domain.Company;

/** Repository for Attachment entities. */
@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

  /** Find all attachments for a company, ordered by upload date descending. */
  List<Attachment> findByCompanyOrderByUploadedAtDesc(Company company);

  /** Find attachments for a company with pagination. */
  Page<Attachment> findByCompany(Company company, Pageable pageable);

  /** Find an attachment by company and checksum (for deduplication). */
  Optional<Attachment> findByCompanyAndChecksumSha256(Company company, String checksumSha256);

  /** Check if an attachment with the same checksum already exists. */
  boolean existsByCompanyAndChecksumSha256(Company company, String checksumSha256);

  /** Find attachments linked to a specific entity. */
  @Query(
      "SELECT a FROM Attachment a JOIN AttachmentLink l ON l.attachment = a "
          + "WHERE l.entityType = :entityType AND l.entityId = :entityId "
          + "ORDER BY a.uploadedAt DESC")
  List<Attachment> findByEntityTypeAndEntityId(
      @Param("entityType") com.example.application.domain.AttachmentLink.EntityType entityType,
      @Param("entityId") Long entityId);

  /** Count attachments for a company. */
  long countByCompany(Company company);

  /** Search attachments by filename (case-insensitive). */
  @Query(
      "SELECT a FROM Attachment a WHERE a.company = :company "
          + "AND LOWER(a.filename) LIKE LOWER(CONCAT('%', :filename, '%')) "
          + "ORDER BY a.uploadedAt DESC")
  List<Attachment> searchByFilename(
      @Param("company") Company company, @Param("filename") String filename);
}
