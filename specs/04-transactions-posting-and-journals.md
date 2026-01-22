# Transactions, Posting, and Journals

## Topic sentence
The system records business transactions quickly and posts them into an immutable general ledger with full reversal support.

## JTBD
As a bookkeeper, I want fast, accurate entry screens for payments, receipts, and journals, and I want postings that an accountant trusts.

## Scope
- Transaction types (v1):
    - Payment (cashbook)
    - Receipt (cashbook)
    - Journal (including reversing journal)
    - Transfer between accounts
- Transaction lifecycle:
    - DRAFT → POSTED
    - POSTED transactions cannot be edited; user creates adjustment/reversal.
- Posting engine:
    - Produces balanced debit/credit ledger entries
    - Supports dimensions (department/cost centre) if enabled (see later spec)
- Attachments are supported via spec 13.

## Domain model
- Transaction(id, companyId, type, date, description, status, createdBy, createdAt, postedAt?)
- TransactionLine(id, transactionId, accountId, amount, direction[DR|CR], taxCode?, departmentId?, memo?)
- LedgerEntry(id, companyId, transactionId, lineId, date, accountId, amountDr, amountCr, taxCode?, departmentId?)
- ReversalLink(originalTransactionId, reversingTransactionId)

## Rules
- Every POSTED transaction produces ledger entries exactly once.
- Posting is idempotent and rejects double-post attempts.
- Reversals:
    - A “Reverse” action creates a new transaction with inverted lines and links it.
- Validation:
    - Total DR == total CR before posting.
    - Account must be active.
    - Date must be within an OPEN period.

## UX notes (Vaadin)
- “Single keystroke access”: hotkeys to open Payments/Receipts/Journals.
- Entry screen:
    - header (date, payee/payer, reference, bank account)
    - lines grid (account, tax, memo, amount)
    - live balance indicator (must be 0 to post)
- Posting action requires explicit confirmation.

## Acceptance criteria
- Posting a transaction creates balanced ledger entries and updates reports immediately.
- Editing a posted transaction is not possible; UI directs to “Reverse / Adjust”.
- Reversing a transaction results in net zero effect when both are included.
- Attempting to post into a locked period fails with a clear error.
