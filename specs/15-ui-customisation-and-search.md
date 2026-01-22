# UI Customisation and Global Search

## Topic sentence
The system provides fast navigation, configurable grids/forms, and powerful search expressions across core entities.

## JTBD
As a power user, I want “single keystroke access”, customisable lists, and the ability to find anything instantly.

## Scope
- Global search:
    - search across transactions, contacts, products, accounts, invoices, bills
    - supports simple query expressions (e.g., `type:invoice status:overdue amount>10000 older_than:60d`)
- List customisation:
    - reorder columns, show/hide, saved views per user
    - calculated columns (phase 2)
    - filters + quick text search
- Entry screen customisation (phase 2):
    - optional fields, tab order, defaults, dropdown pick-lists
    - validations (regex, required, lookup constraints)

## Domain model
- SavedView(id, companyId, userId, entityType, name, columnsJson, filtersJson, sortJson)
- SearchIndex (implementation detail; can start DB-based then add full-text)

## Acceptance criteria
- Users can search by free text and by field filters for all major entities.
- Users can save a custom grid layout and it persists per user.
- Search results respect company scoping and permissions.
