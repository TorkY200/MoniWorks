# Budgeting, Departments, and KPIs

## Topic sentence
The system supports departmental (cost-centre) dimensions and budgets with budget-vs-actual reporting and KPI time series.

## JTBD
As a manager, I want to see performance vs budget by department and track KPIs for decision making.

## Scope
- Departments:
    - code up to 5 alphanumeric, name, grouping, classification
    - optionally auto-create subledger structure (later); v1 uses a dimension column
- Budgets:
    - two budgets (Budget A and Budget B)
    - budget amounts per account per period (and optionally department)
    - import/copy-paste support (CSV)
- KPIs:
    - monthly values stored off-ledger for 7+ years
    - used in dashboards/reports

## Domain model
- Department(id, companyId, code, name, group?, classification?, active)
- Budget(id, companyId, name, type[A|B], currency)
- BudgetLine(id, budgetId, periodId, accountId, departmentId?, amount)
- KPI(id, companyId, code, name, unit)
- KPIValue(id, kpiId, periodId, value)

## Rules
- Department is an optional dimension on transaction lines; if enabled, it flows to ledger entries.
- Budget-vs-actual uses the same reporting filters as financial statements.

## Acceptance criteria
- Users can filter P&L by department and see correct totals.
- Budget-vs-actual report matches stored budget lines and actual ledger totals per period.
- KPI values can be entered/imported and charted over time.
