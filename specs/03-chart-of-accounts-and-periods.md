# Chart of Accounts and Period Control

## Topic sentence
The system maintains a flexible chart of accounts with period controls and produces trial balance, balance sheet, and income statement accurately.

## JTBD
As a business, I want accounts that match how we operate, and I need reliable financial statements and period controls without forced rollovers.

## Scope
- Create/edit accounts with:
    - 7-char free-form code (plus optional alternate code)
    - type (Asset/Liability/Equity/Income/Expense)
    - tax defaults (optional)
    - security level
- Period system:
    - fiscal years + periods, with ability to keep many periods open
    - close/lock periods to prevent posting changes
- Core statements:
    - Trial Balance
    - Balance Sheet
    - Profit & Loss (Income Statement)

## Domain model
- Account(id, companyId, code, altCode?, name, type, parentId?, active, taxDefaultCode?, securityLevel)
- FiscalYear(id, companyId, startDate, endDate, label)
- Period(id, fiscalYearId, index, startDate, endDate, status[OPEN|LOCKED])
- AccountBalanceSnapshot(optional later)

## Rules
- Account code uniqueness per company.
- Posting into LOCKED periods is forbidden.
- No end-of-year rollover required: reporting derives from ledger entries + period ranges.

## UX notes (Vaadin)
- Accounts view: tree grid (parent/child), inline edit or dialog edit.
- Periods view: list of periods with status toggles (admin only).

## Acceptance criteria
- Trial Balance debits equal credits for any date range.
- Balance Sheet balances (Assets = Liabilities + Equity) for any date range.
- P&L totals match sum of income/expense entries for the range.
- Locking a period prevents posting or unposting transactions dated in that period.
