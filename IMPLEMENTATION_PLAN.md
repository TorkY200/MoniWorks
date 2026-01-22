# MoniWorks Implementation Plan

## Current Status
- Project initialized with Spring Boot 4.0.1, Vaadin 25.0.3, Java 21
- **Phase 1 Foundation COMPLETE** - Tag: 0.0.1
- **Phase 2 Core Accounting COMPLETE** - Tag: 0.0.2
- All 13 tests passing (PostingServiceTest: 7, ReportingServiceTest: 5, ApplicationTest: 1)
- Core domain entities created: Company, User, Account, FiscalYear, Period, Transaction, TransactionLine, LedgerEntry, TaxCode, Department, Role, Permission, CompanyMembership, AuditEvent
- Database configured: H2 for development, PostgreSQL for production
- Flyway migration V1__initial_schema.sql created
- All repository interfaces created
- Full service layer: CompanyService, AccountService, TransactionService, PostingService, ReportingService, UserService, AuditService, CompanyContextService, TaxCodeService, FiscalYearService
- Full UI views: MainLayout, LoginView, DashboardView, TransactionsView, AccountsView, PeriodsView
- Security configuration with SecurityConfig and UserDetailsServiceImpl (using VaadinSecurityConfigurer API)

## Release 1 (SLC) - Target Features
Per specs, Release 1 must deliver:
1. Single company support (multi-tenancy foundation) - DONE
2. Chart of Accounts with hierarchical structure - DONE
3. Fiscal Years and Periods with lock/unlock - DONE
4. Cashbook transactions (Payments, Receipts, Journals) - DONE
5. Posting to immutable ledger - DONE
6. GST/Tax coding and returns - Tax codes on transactions done, returns pending
7. Bank import (QIF/OFX) and reconciliation - Partial - bank account linking done, import service created
8. Financial reports (Trial Balance, P&L, Balance Sheet) - DONE
9. Attachments for source documents - PENDING
10. Audit trail - Core logging done, UI pending

## Implementation Order

### Phase 1: Foundation (COMPLETE) - Tag: 0.0.1
- [x] Project setup with Maven, Spring Boot, Vaadin
- [x] Add database dependencies (PostgreSQL, H2, JPA, Flyway)
- [x] Configure database connections (H2 for dev, PostgreSQL for prod)
- [x] Create core domain entities
- [x] Create Flyway migrations (V1__initial_schema.sql)
- [x] Create repository layer
- [x] Create basic service layer
- [x] Create Vaadin UI shell with AppLayout
- [x] Create security configuration

### Phase 2: Core Accounting (COMPLETE) - Tag: 0.0.2
- [x] CompanyContextService for session-scoped company management
- [x] TaxCodeService for tax code management
- [x] FiscalYearService for fiscal year and period management
- [x] AccountsView with TreeGrid, search, add/edit dialogs
- [x] PeriodsView with fiscal year selection and period lock/unlock
- [x] TransactionsView with full CRUD, transaction lines grid, posting, reversals
- [x] PostingService with validation (balanced entries, open periods, active accounts)

### Phase 3: Tax & Bank (In Progress)
- [x] Tax codes management UI
  - Created TaxCodesView.java with full CRUD UI for managing tax codes
  - Features: Grid display, add/edit dialogs, search/filter, activate/deactivate
  - Added "Create default NZ GST codes" button for quick setup
  - Added Tax Codes navigation item to MainLayout with calculator icon
- [ ] Tax calculation on transactions
- [x] Bank account linking (mark accounts as bank accounts)
  - Added bank account fields to Account entity (isBankAccount, bankName, bankNumber, bankCurrency)
  - Created V2__bank_accounts.sql migration
  - Created BankStatementImport, BankFeedItem, AllocationRule entities
  - Created BankImportService with QIF, OFX, and CSV parsing
  - Updated AccountsView with bank account configuration fields
- [x] Bank import (OFX/QIF parsing)
  - Implemented in BankImportService with support for QIF, OFX, and CSV formats
- [ ] Reconciliation matching UI

### Phase 4: Reports & Polish
- [x] Trial Balance report view
  - Created ReportsView.java with tabbed interface for all 3 financial reports
  - Date range pickers (from_date to to_date) for Trial Balance filtering
  - Account, debit, credit, and balance columns with proper formatting
- [x] P&L report view
  - Date range pickers for period selection in P&L tab
  - Revenue, Expense, and Net Profit/Loss calculations and display
  - Formatted money display with totals rows
- [x] Balance Sheet report view
  - As-of date picker for balance sheet snapshot
  - Assets, Liabilities, and Equity sections with hierarchical account structure
  - Balance status indicators showing BALANCED/OUT OF BALANCE
- [ ] GST return generation
- [ ] Attachments support
- [ ] Audit event logging UI

## ReportsView Implementation Details
- Created comprehensive ReportsView.java with tabbed interface for Trial Balance, P&L, and Balance Sheet
- Date range pickers implemented for Trial Balance and P&L reports
- As-of date picker for Balance Sheet reports to show account balances at specific point in time
- Balance status indicators display current balance status (BALANCED/OUT OF BALANCE)
- Formatted money display using CurrencyFormat utility for consistent currency presentation
- Totals rows implemented for all report sections showing subtotals and final balances
- Added Reports navigation item to MainLayout with chart-bar icon for easy access
- Reports leverage existing ReportingService for data retrieval

## Lessons Learned
- VaadinWebSecurity deprecated in Vaadin 24.8+ - use VaadinSecurityConfigurer.vaadin() instead
- Test profile should use hibernate.ddl-auto=create-drop with Flyway disabled to avoid schema conflicts
- AuditService should create its own ObjectMapper rather than injecting as bean for test isolation
- TreeGrid requires TreeDataProvider with proper parent-child hierarchy setup
- @VaadinSessionScope for session-scoped beans (like CompanyContextService)

## Technical Notes
- Build: `./mvnw compile`
- Test: `./mvnw test`
- Run: `./mvnw spring-boot:run` (starts on http://localhost:8080)
- Package: `./mvnw package -Pproduction`
- Run with production profile: `java -jar target/moniworks-1.0-SNAPSHOT.jar --spring.profiles.active=prod`
- mvnw needs `chmod +x` after clone
- H2 console available in dev mode at http://localhost:8080/h2-console

## Architecture Decisions
- Money stored as BigDecimal with 2 decimal places (minor units)
- Dates: LocalDate for accounting, Instant for audit timestamps
- Ledger entries are immutable - corrections via reversals only
- Multi-tenant: All entities include companyId, enforced in queries
- Session-scoped CompanyContextService manages current company per user session
