# Products, Pricing, and Inventory

## Topic sentence
The system maintains a product database for consistent pricing and optional inventory tracking with audit trails.

## JTBD
As a business, I want products/services reusable in sales/purchases with accurate pricing and (optionally) stock control.

## Scope
- Products/services:
    - code (<= 31 chars), name, category, buy/sell prices, tax defaults
    - notes (“sticky note prompts”)
    - optional barcode and image
- Inventory (phased):
    - v1: non-inventory products + quantity on invoices (no valuation)
    - v2: inventory valuation, serial/batch, warehouses, BOM

## Domain model
- Product(id, companyId, code, name, description, category?, buyPrice, sellPrice, taxCode?, isInventoried, barcode?, imageAttachmentId?, stickyNote?, active)
- StockLocation(id, companyId, code<=15, name) (later)
- InventoryMovement(id, companyId, productId, locationId?, date, qtyChange, reason, sourceTransactionId?) (later)

## UX notes (Vaadin)
- Products grid with filters by category, active, inventoried.
- Product detail: pricing, defaults, notes.

## Acceptance criteria
- Product codes are unique per company.
- Selecting a product on an invoice line fills description, unit price, tax defaults.
- Sticky note appears when product is selected in a transaction/invoice.
