# Accounts Receivable (AR): Invoicing and Receipts Allocation

## Topic sentence
The system issues invoices, tracks amounts owed, produces statements, and allocates receipts to open items.

## JTBD
As a business, I want to bill customers professionally and reduce overdue debt through accurate tracking and statements.

## Scope
- Sales invoices:
    - draft → issued/posted
    - line items by product or free-form service lines (long descriptions)
    - tax handling per spec 06
    - PDF generation via templates (initial “template editor”, not full designer)
- Receivables tracking:
    - open-item (invoice-based) optional
    - ageing reports
- Receipts allocation:
    - allocate receipt to one or many invoices (partial payments, overpayments)
    - auto-allocation suggestions when importing bank statements (hooks from spec 05)
- Statements:
    - open-item or balance-forward (choose one first; open-item preferred)

## Domain model
- SalesInvoice(id, companyId, invoiceNumber, contactId, issueDate, dueDate, status[DRAFT|ISSUED|VOID], currency?, totalsJson, postedTransactionId?)
- SalesInvoiceLine(id, invoiceId, productId?, description, qty, unitPrice, accountId, taxCode, lineTotal)
- ReceivableAllocation(id, receiptTransactionId, invoiceId, amount)
- StatementRun(id, companyId, runDate, criteriaJson, outputAttachmentId?)

## Rules
- Issuing an invoice posts accounting entries (AR control + income + tax).
- Voiding/credit note is handled via reversal/credit transaction patterns (no silent edits).

## UX notes (Vaadin)
- Invoice list with status filters + “send/export PDF”.
- Invoice edit view with line grid and totals.
- Allocation UI: “Allocate receipt” dialog with outstanding invoices list.

## Acceptance criteria
- Issuing an invoice creates correct ledger entries and updates AR ageing.
- Statements reflect outstanding balances and match ledger totals.
- Allocation supports partial payments and leaves correct remaining balances.
