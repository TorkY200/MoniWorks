# Tenancy and User Permissions

## Topic sentence
The system isolates data per company and enforces role-based access controls over features and sensitive accounts.

## JTBD
As an owner/bookkeeper, I want multiple companies with separate ledgers, and I want users to only see/do what they’re permitted to.

## Scope
- Multiple companies (tenants) per installation.
- User accounts, login, roles, permissions.
- Company membership (a user can belong to many companies).
- Account-level security (e.g., payroll accounts restricted).

## Non-goals (v1)
- External SSO/OAuth (can be added later).
- Field-level ACL everywhere (start with feature + account-level).

## Domain model
- Company(id, name, country, baseCurrency, fiscalYearStart, settingsJson)
- User(id, email, displayName, passwordHash, status)
- CompanyMembership(companyId, userId, roleId, status)
- Role(id, companyId nullable for global templates, name)
- Permission(code, description) + RolePermission(roleId, permissionCode)
- AccountSecurityLevel(accountId, level) + UserMaxSecurityLevel(userId, companyId, level)

## Permission groups (initial)
- ADMIN: manage users/roles, settings, chart of accounts, periods.
- BOOKKEEPER: enter transactions, reconcile, run reports, manage contacts/products.
- READONLY: view everything except restricted accounts.
- AP_CLERK / AR_CLERK (optional): subset of AP/AR.

## Key workflows
1. Create company → create first admin membership.
2. Invite user by email → user sets password → accept membership.
3. Switch company in UI → all queries scoped to company.
4. Restrict access to sensitive GL accounts by “security level”.

## Acceptance criteria
- A user cannot access data from a company they are not a member of (server-side enforced).
- A READONLY user cannot create/edit/post transactions.
- A user below an account’s security level cannot view it in reports, grids, or drilldowns.
- Company switcher shows only companies user belongs to.
