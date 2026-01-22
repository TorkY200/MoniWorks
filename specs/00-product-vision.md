# Product Vision: Vaadin Accounting Webapp

## One-sentence vision
A self-hostable, multi-company accounting system that makes day-to-day bookkeeping fast and safe, while producing accountant-grade ledgers, tax returns, and reports.

## Primary audience
- Small/medium businesses in GST/VAT jurisdictions (NZ first-class).
- Accountants/bookkeepers supporting multiple clients.
- Owners who want “cashbook speed” with “general ledger correctness”.

## Release framing (SLC)
- **Release 1 (Simple/Lovable/Complete):** Single company + cashbook transactions + chart of accounts + GST coding + bank import + bank reconciliation + P&L/Balance Sheet + attachments.
- **Release 2:** A/R invoicing + statements + receipts allocation + A/P bills + payments runs.
- **Release 3:** Inventory/products + departments + budgeting + multicurrency + jobs.

## Core invariants (non-negotiable)
- Posted accounting entries are **immutable**; corrections are made via reversals/adjustments.
- Full audit trail for financial integrity.
- Data model supports **7+ years** history and reporting comparisons.
- UX is “single-keystroke access” (fast navigation + search) with Vaadin grids/forms.

## Data boundaries
- The system stores accounting data in a relational DB.
- Large binary documents (PDF scans, images) are stored outside the core DB (file/object storage) with DB references.

## Glossary (shared)
- **Company**: a separate set of accounts (tenant).
- **Period**: accounting period within fiscal year (can keep many open).
- **Account**: GL account (with code, type).
- **Transaction**: user-entered business event (cashbook, invoice, bill, journal).
- **Posting**: converting a transaction into ledger entries.
- **Ledger Entry**: debit/credit line affecting accounts (and dimensions).
- **Tax Code**: GST/VAT handling + rate + reporting box mapping.
- **Attachment**: PDF/image linked to a transaction or master data.

## Out of scope (for now)
- Payroll and payruns.
- Full WYSIWYG forms designer rivaling MoneyWorks Forms Designer (we’ll do “template editing” first).
- Regulatory e-filing integrations.
