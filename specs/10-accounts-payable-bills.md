# Accounts Payable (AP): Bills and Payments

## Topic sentence
The system records supplier bills, tracks what is owed, and supports batch payments with remittance outputs.

## JTBD
As a business, I want to enter supplier invoices quickly and pay them efficiently while preserving tax correctness and auditability.

## Scope
- Supplier bills:
    - draft → posted
    - coding by account or by product lines
    - attachments (invoice PDF)
- Payables tracking:
    - aged payables report
- Payments:
    - single payment and batch payment runs
    - remittance advice output (PDF/email later)
    - optional “direct credit file” export (bank-specific; later)

## Domain model
- SupplierBill(id, companyId, billNumber, contactId, billDate, dueDate, status[DRAFT|POSTED|VOID], totalsJson, postedTransactionId?)
- SupplierBillLine(id, billId, productId?, description, qty, unitPrice, accountId, taxCode, lineTotal)
- PayableAllocation(id, paymentTransactionId, billId, amount)
- PaymentRun(id, companyId, runDate, bankAccountId, status[DRAFT|COMPLETED], itemsJson, outputAttachmentId?)

## Rules
- Posting a bill posts AP control + expense/asset + tax.
- Payment allocations support partial payments and overpayments.

## UX notes (Vaadin)
- Bills grid with due filters.
- “Create payment run” wizard: choose bills by due date/vendor/category, review, post.

## Acceptance criteria
- Posting bills updates AP ageing correctly.
- Batch payment run produces a single posting per payment (or per vendor, defined consistently).
- Remittance output contains the bills paid and amounts allocated.
