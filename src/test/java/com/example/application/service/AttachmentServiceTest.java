package com.example.application.service;

import com.example.application.domain.*;
import com.example.application.domain.AttachmentLink.EntityType;
import com.example.application.repository.AttachmentLinkRepository;
import com.example.application.repository.AttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AttachmentService.
 * Tests file upload, storage, linking, and retrieval functionality.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private AttachmentLinkRepository linkRepository;

    @Mock
    private AuditService auditService;

    @TempDir
    Path tempDir;

    private AttachmentService attachmentService;

    private Company company;
    private User user;

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentService(attachmentRepository, linkRepository, auditService);

        // Set the storage path to temp directory
        ReflectionTestUtils.setField(attachmentService, "storagePath", tempDir.toString());

        company = new Company();
        company.setId(1L);
        company.setName("Test Company");

        user = new User("test@example.com", "Test User");
        user.setId(1L);
    }

    @Test
    void uploadFile_ValidPdf_CreatesAttachment() {
        // Given
        String filename = "invoice.pdf";
        String mimeType = "application/pdf";
        byte[] content = "PDF content here".getBytes();

        when(attachmentRepository.findByCompanyAndChecksumSha256(any(), any())).thenReturn(Optional.empty());
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment att = invocation.getArgument(0);
            att.setId(1L);
            return att;
        });

        // When
        Attachment result = attachmentService.uploadFile(company, filename, mimeType, content, user);

        // Then
        assertNotNull(result);
        assertEquals(filename, result.getFilename());
        assertEquals(mimeType, result.getMimeType());
        assertEquals((long) content.length, result.getSize());
        assertNotNull(result.getChecksumSha256());
        assertEquals(64, result.getChecksumSha256().length()); // SHA-256 hex length

        verify(attachmentRepository).save(any(Attachment.class));
        verify(auditService).logEvent(eq(company), eq(user), eq("ATTACHMENT_UPLOADED"),
            eq("ATTACHMENT"), eq(1L), contains("invoice.pdf"));
    }

    @Test
    void uploadFile_InvalidMimeType_ThrowsException() {
        // Given
        String filename = "script.exe";
        String mimeType = "application/x-executable";
        byte[] content = "binary content".getBytes();

        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            attachmentService.uploadFile(company, filename, mimeType, content, user));

        assertTrue(exception.getMessage().contains("File type not allowed"));
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void uploadFile_ExceedsMaxSize_ThrowsException() {
        // Given
        String filename = "large.pdf";
        String mimeType = "application/pdf";
        byte[] content = new byte[11 * 1024 * 1024]; // 11 MB

        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            attachmentService.uploadFile(company, filename, mimeType, content, user));

        assertTrue(exception.getMessage().contains("File too large"));
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void uploadFile_DuplicateChecksum_ReturnsExisting() {
        // Given
        String filename = "invoice.pdf";
        String mimeType = "application/pdf";
        byte[] content = "PDF content here".getBytes();

        Attachment existingAttachment = new Attachment();
        existingAttachment.setId(99L);
        existingAttachment.setFilename("existing.pdf");

        when(attachmentRepository.findByCompanyAndChecksumSha256(any(), any()))
            .thenReturn(Optional.of(existingAttachment));

        // When
        Attachment result = attachmentService.uploadFile(company, filename, mimeType, content, user);

        // Then
        assertEquals(99L, result.getId());
        assertEquals("existing.pdf", result.getFilename());
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void uploadFile_AllowedImageTypes_Succeeds() {
        // Given
        when(attachmentRepository.findByCompanyAndChecksumSha256(any(), any())).thenReturn(Optional.empty());
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment att = invocation.getArgument(0);
            att.setId(1L);
            return att;
        });

        String[] allowedTypes = {"image/jpeg", "image/png", "image/gif", "image/webp"};
        byte[] content = "image content".getBytes();

        for (String mimeType : allowedTypes) {
            // When
            Attachment result = attachmentService.uploadFile(company, "image.test", mimeType, content, user);

            // Then
            assertNotNull(result);
        }
    }

    @Test
    void linkToEntity_CreatesLink() {
        // Given
        Attachment attachment = new Attachment();
        attachment.setId(1L);

        when(linkRepository.findByAttachmentAndEntityTypeAndEntityId(any(), any(), anyLong()))
            .thenReturn(Optional.empty());
        when(linkRepository.save(any(AttachmentLink.class))).thenAnswer(invocation -> {
            AttachmentLink link = invocation.getArgument(0);
            link.setId(1L);
            return link;
        });

        // When
        AttachmentLink result = attachmentService.linkToEntity(attachment, EntityType.TRANSACTION, 100L);

        // Then
        assertNotNull(result);
        assertEquals(EntityType.TRANSACTION, result.getEntityType());
        assertEquals(100L, result.getEntityId());

        verify(linkRepository).save(any(AttachmentLink.class));
    }

    @Test
    void linkToEntity_ExistingLink_ReturnsExisting() {
        // Given
        Attachment attachment = new Attachment();
        attachment.setId(1L);

        AttachmentLink existingLink = new AttachmentLink(attachment, EntityType.TRANSACTION, 100L);
        existingLink.setId(99L);

        when(linkRepository.findByAttachmentAndEntityTypeAndEntityId(attachment, EntityType.TRANSACTION, 100L))
            .thenReturn(Optional.of(existingLink));

        // When
        AttachmentLink result = attachmentService.linkToEntity(attachment, EntityType.TRANSACTION, 100L);

        // Then
        assertEquals(99L, result.getId());
        verify(linkRepository, never()).save(any());
    }

    @Test
    void getFileContent_ValidFile_ReturnsContent() throws Exception {
        // Given - First upload a file
        String filename = "test.pdf";
        String mimeType = "application/pdf";
        byte[] originalContent = "Test PDF content for retrieval".getBytes();

        when(attachmentRepository.findByCompanyAndChecksumSha256(any(), any())).thenReturn(Optional.empty());
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment att = invocation.getArgument(0);
            att.setId(1L);
            return att;
        });

        Attachment attachment = attachmentService.uploadFile(company, filename, mimeType, originalContent, user);

        // When
        byte[] retrievedContent = attachmentService.getFileContent(attachment);

        // Then
        assertArrayEquals(originalContent, retrievedContent);
    }

    @Test
    void deleteAttachment_RemovesFileAndRecord() throws Exception {
        // Given - First upload a file
        String filename = "to-delete.pdf";
        String mimeType = "application/pdf";
        byte[] content = "Delete me".getBytes();

        when(attachmentRepository.findByCompanyAndChecksumSha256(any(), any())).thenReturn(Optional.empty());
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment att = invocation.getArgument(0);
            att.setId(1L);
            att.setCompany(company);
            return att;
        });

        Attachment attachment = attachmentService.uploadFile(company, filename, mimeType, content, user);

        // When
        attachmentService.deleteAttachment(attachment, user);

        // Then
        verify(linkRepository).deleteByAttachment(attachment);
        verify(attachmentRepository).delete(attachment);
        verify(auditService).logEvent(eq(company), eq(user), eq("ATTACHMENT_DELETED"),
            eq("ATTACHMENT"), eq(1L), contains("to-delete.pdf"));
    }

    @Test
    void checksumCalculation_SameContent_SameChecksum() {
        // Given
        byte[] content = "Same content".getBytes();

        when(attachmentRepository.findByCompanyAndChecksumSha256(any(), any())).thenReturn(Optional.empty());
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment att = invocation.getArgument(0);
            att.setId(1L);
            return att;
        });

        // When
        Attachment att1 = attachmentService.uploadFile(company, "file1.pdf", "application/pdf", content, user);

        reset(attachmentRepository);
        when(attachmentRepository.findByCompanyAndChecksumSha256(any(), any())).thenReturn(Optional.empty());
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment att = invocation.getArgument(0);
            att.setId(2L);
            return att;
        });

        Attachment att2 = attachmentService.uploadFile(company, "file2.pdf", "application/pdf", content, user);

        // Then
        assertEquals(att1.getChecksumSha256(), att2.getChecksumSha256());
    }
}
