# Attachments, Audit Trails, and Accounting Integrity

## Topic sentence
The system stores source documents and maintains immutable audit trails for all posted accounting events and key master-data changes.

## JTBD
As an accountant/owner, I want proof of “who changed what and when” and I want original documents attached for verification.

## Scope
- Attachments:
    - PDF/image upload
    - link to transactions, invoices, bills, products, contacts
    - store outside DB with checksum and metadata
- Audit trail:
    - append-only log of:
        - posted/unposted actions
        - reversals
        - edits to master data (accounts, contacts, products, tax codes)
        - login events (optional)
- Integrity rules:
    - posted transactions immutable
    - changes via reversals/adjustments only
    - structural changes logged
- Security hardening basics:
    - server-side authorization checks for every route/action
    - CSRF/session protections via Spring Security defaults

## Domain model
- Attachment(id, companyId, filename, mimeType, size, checksumSha256, storageKey, uploadedAt, uploadedBy)
- AttachmentLink(id, attachmentId, entityType, entityId)
- AuditEvent(id, companyId, at, actorUserId, type, entityType, entityId, summary, detailsJson)

## Acceptance criteria
- Uploading an attachment persists it and allows retrieval from linked entity screens.
- Posting a transaction produces an audit event with actor and timestamp.
- Editing a chart-of-accounts record produces an audit event capturing before/after (at least key fields).
- Users cannot delete audit events; retention policy is configurable but defaults to “keep”.
