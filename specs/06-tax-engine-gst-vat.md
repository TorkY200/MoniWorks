# Tax Engine: GST/VAT

## Topic sentence
The system calculates and reports GST/VAT accurately across transactions, supporting cash or invoice basis where configured.

## JTBD
As a business, I want tax handled automatically and I want a trustworthy GST return with an audit trail.

## Scope
- Tax codes and rates (NZ GST default):
    - standard, zero-rated, exempt, out-of-scope
    - inclusive/exclusive entry support
- Tax defaults by account and by contact
- Tax reporting:
    - GST return for a selected period range
    - drilldown to underlying transactions
- Basis:
    - Cash basis (based on payments/receipts)
    - Invoice/accrual basis (if AR/AP enabled)
    - Configurable per company

## Domain model
- TaxCode(code, companyId, name, rate, type[STANDARD|ZERO|EXEMPT|OUT], reportBoxMappingJson)
- TaxLine(id, ledgerEntryId, taxCode, taxableAmount, taxAmount, jurisdiction)
- TaxReturn(id, companyId, startDate, endDate, basis, generatedAt, generatedBy, totalsJson)
- TaxReturnLine(id, taxReturnId, boxCode, amount)

## Rules
- Tax amount rounding rules are consistent and documented (e.g., half-up).
- Each posted transaction line with a tax code yields corresponding tax lines.
- Return generation is deterministic and reproducible for the same ledger state.

## UX notes (Vaadin)
- Tax codes management view (admin/bookkeeper).
- GST return view:
    - summary boxes
    - drilldown by box → list of transactions → transaction detail.

## Acceptance criteria
- Entering a taxable line produces correct tax amount per rate and rounding rules.
- GST return totals match sum of included tax lines for the period and basis.
- Drilldown shows exact source transactions contributing to each box.
