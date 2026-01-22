# Engineering Rules: Vaadin + Ralph Loop + vaadin-mcp

## Stack constraints (single-language full stack)
- Java + Vaadin Flow (Spring Boot recommended).
- Server-rendered Vaadin UI, no SPA frontend framework required.
- JPA/Hibernate + Flyway migrations.
- PostgreSQL default, H2 for fast tests if needed.
- JUnit5 + Testcontainers for integration tests.

## Claude Code / Ralph requirements
Claude (Ralph loop) must:
- Treat `specs/*` as source of truth.
- Create/update `IMPLEMENTATION_PLAN.md` via planning loop.
- Use backpressure: compile + tests + format + static checks before committing.
- Avoid placeholders; “done” means acceptance criteria satisfied.

## Mandatory tool usage (Claude Code)
Claude must use:
- **vaadin-mcp server** for Vaadin-specific scaffolding/search:
    - Generate/modify Vaadin views, forms, grids, navigation routes.
    - Query Vaadin component APIs/patterns.
    - Enforce consistent Vaadin UI patterns (AppLayout, Grid+Editor, dialogs).
- Common Claude Code skills/plugins (names vary by install, but intent is required):
    - repo-wide search (ripgrep equivalent)
    - file edit/patch tool
    - build/test runner
    - git commit tool

## Vaadin UI conventions (signs for Ralph)
- Top-level shell: `AppLayout` with left nav + company switcher + global search.
- “List → Detail” pattern:
    - A Vaadin `Grid` for lists (sortable, filterable).
    - A detail view or `Dialog` form for editing.
    - Keyboard shortcuts for create/save/search.
- All money amounts:
    - Store as integer minor units (e.g., cents) or `BigDecimal` with currency; pick one pattern and use consistently.
- Dates/times:
    - Store `LocalDate` for accounting dates; `Instant` for audit timestamps.

## Acceptance-driven backpressure
For each spec’s acceptance criteria:
- Create unit tests for rule engines and posting logic.
- Create integration tests for “posting” and “reports totals”.
- UI tests are optional at first, but at least smoke-test key views (route loads + permissions).

## Repository structure expectation (guidance, not strict)
- `src/main/java/...` domain, services, Vaadin UI, security.
- `src/test/java/...` unit + integration.
- `src/main/resources/db/migration` Flyway SQL.
- `storage/` or configurable path for attachments.
