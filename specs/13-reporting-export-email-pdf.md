# Reporting, Export, Email, and PDF Output

## Topic sentence
The system generates professional reports and documents with drilldown, exports, and shareable PDFs.

## JTBD
As a business, I want answers quickly (on-screen enquiries) and I want reports I can send to others.

## Scope
- Reports:
    - Trial Balance, P&L, Balance Sheet (from spec 03)
    - Cashflow and bank register (basic)
    - GST return (from spec 06)
    - AR/AP ageing (from specs 09/10)
    - Budget vs actual (from spec 12)
- Drilldown:
    - click totals → list of contributing transactions → open transaction
- Export:
    - PDF for all reports
    - Excel/CSV export for lists and reports
- Email:
    - v1: generate PDF and download; “email sending” can be stubbed behind an interface but not required
- Dashboard:
    - configurable tiles: cash balance, overdue AR/AP, income trend, GST due estimate

## PDF/template approach (v1)
- Use a small set of built-in templates:
    - Invoice, statement, remittance, core reports
- Allow “template settings”:
    - logo upload, company details, footer text, paper size
- Full forms designer is a later enhancement.

## Acceptance criteria
- Every report supports on-screen preview + PDF export.
- Totals in exports match on-screen values.
- Drilldown paths exist for all major totals.
- Dashboard tiles respect user permissions and account security levels.
