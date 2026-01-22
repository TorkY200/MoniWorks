package com.example.application.repository;

import com.example.application.domain.Attachment;
import com.example.application.domain.AttachmentLink;
import com.example.application.domain.AttachmentLink.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AttachmentLink entities.
 */
@Repository
public interface AttachmentLinkRepository extends JpaRepository<AttachmentLink, Long> {

    /**
     * Find all links for an attachment.
     */
    List<AttachmentLink> findByAttachment(Attachment attachment);

    /**
     * Find all links for an entity.
     */
    List<AttachmentLink> findByEntityTypeAndEntityId(EntityType entityType, Long entityId);

    /**
     * Find a specific link.
     */
    Optional<AttachmentLink> findByAttachmentAndEntityTypeAndEntityId(
        Attachment attachment, EntityType entityType, Long entityId);

    /**
     * Check if a link already exists.
     */
    boolean existsByAttachmentAndEntityTypeAndEntityId(
        Attachment attachment, EntityType entityType, Long entityId);

    /**
     * Delete all links for an entity (useful when entity is deleted).
     */
    void deleteByEntityTypeAndEntityId(EntityType entityType, Long entityId);

    /**
     * Delete all links for an attachment.
     */
    void deleteByAttachment(Attachment attachment);

    /**
     * Count links for an attachment.
     */
    long countByAttachment(Attachment attachment);

    /**
     * Count attachments for an entity.
     */
    long countByEntityTypeAndEntityId(EntityType entityType, Long entityId);

    /**
     * Find all attachments linked to a specific entity.
     */
    @Query("SELECT l.attachment FROM AttachmentLink l " +
           "WHERE l.entityType = :entityType AND l.entityId = :entityId")
    List<Attachment> findAttachmentsByEntity(
        @Param("entityType") EntityType entityType,
        @Param("entityId") Long entityId
    );
}
