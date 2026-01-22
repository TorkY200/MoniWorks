# Bank Import and Bank Reconciliation

## Topic sentence
The system imports bank statements and supports point-and-click reconciliation without re-entering statement lines.

## JTBD
As a bookkeeper, I want the bank to do the data entry, and I want reconciliation that is fast and correct.

## Scope
- Import statement files:
    - QIF, OFX, QFX, QBO (and CSV optional)
- Statement staging:
    - imported lines stored as “bank feed items”
    - deduplication by bank id/fitid when available
- Reconciliation screen:
    - show imported bank items and unreconciled ledger items for an account
    - match by amount/date range/description similarity
    - allow create missing transactions during reconciliation
    - allow mark reconciled/unreconciled
- Auto-allocation rules:
    - user-defined rules to suggest coding (income/expense accounts, tax codes)
    - rules can match on description, amount ranges, counterparty, etc.

## Domain model
- BankAccount(id, companyId, accountId(GL), bankName?, bankNumber?, currency)
- BankStatementImport(id, companyId, bankAccountId, importedAt, sourceType, sourceName, hash)
- BankFeedItem(id, importId, postedDate, amount, description, fitId?, rawJson, status[NEW|MATCHED|CREATED|IGNORED])
- ReconciliationMatch(id, bankFeedItemId, transactionId, matchType[AUTO|MANUAL])
- AllocationRule(id, companyId, priority, matchExpression, targetAccountId, targetTaxCode?, memoTemplate?, enabled)

## Rules
- Same FITID cannot be imported twice for same bank account.
- Reconciliation state is stored per ledger transaction line affecting the bank GL account.

## UX notes (Vaadin)
- Two-pane reconcile view:
    - Left: bank feed items
    - Right: candidate ledger items
    - Actions: match, split, create transaction, ignore
- “Create missing transaction” pre-fills fields from bank feed item.
- Show reconciliation status report.

## Acceptance criteria
- Imported statement lines appear as bank feed items with correct totals.
- Matching a bank item reconciles the corresponding ledger effect and removes it from “unreconciled”.
- Users can create a new transaction during reconciliation that posts and reconciles in one flow.
- Allocation rules can auto-suggest coding and improve with priority ordering.
