# Contacts: Customers and Suppliers

## Topic sentence
The system manages customer/supplier master data with roles, defaults, categories, and interaction notes.

## JTBD
As an operator, I want reliable contact records for analysis, invoicing, paying, emailing, and consistent coding defaults.

## Scope
- Contact master records:
    - code (<= 11 chars), name, addresses, phones, emails, website
    - bank details (for remittance/deposit)
    - tax overrides
    - default GL allocation and payment terms
    - categories + color tags
- Multiple people per contact with roles.
- Notes/call logs + follow-up reminders (basic).
- Import/update from CSV (optional v1, but planned).

## Domain model
- Contact(id, companyId, code, name, type[CUSTOMER|SUPPLIER|BOTH], category?, colorTag?, taxOverride?, defaultAccountId?, paymentTerms?, creditLimit?, active)
- ContactPerson(id, contactId, name, email, phone, roleLabel)
- ContactNote(id, contactId, createdAt, createdBy, noteText, followUpDate?)

## UX notes (Vaadin)
- Contacts list with “google-like” search.
- Detail tabs: General, People, Defaults, Notes.
- Bulk email: select by role (deferred if no mail integration).

## Acceptance criteria
- Contact codes are unique per company and searchable.
- Contact default allocation can prefill transaction entry lines.
- Tax override changes default tax behavior on new transactions/invoices.
