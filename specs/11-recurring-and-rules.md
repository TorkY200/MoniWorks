# Recurring Transactions and Allocation Rules

## Topic sentence
The system automates repetitive transactions and applies configurable rules to suggest allocations during data entry and imports.

## JTBD
As a bookkeeper, I want rent/wages/subscriptions to recur automatically and I want coding suggestions to reduce manual work.

## Scope
- Recurring schedules:
    - payments, receipts, invoices, journals (where applicable)
    - patterns: every N days/weeks/months, until date/occurrences/forever
    - optional auto-post vs “create draft”
- Price/description updates on recurrence (for invoice lines).
- Allocation rules:
    - shared with bank import (spec 05) and manual entry assist
    - priority ordering, enable/disable

## Domain model
- RecurringTemplate(id, companyId, templateType, sourceEntityId?, payloadJson, scheduleJson, nextRunDate, status)
- RecurrenceExecutionLog(id, templateId, runAt, result[CREATED|FAILED], createdEntityId?, error?)

## Rules
- Recurrences do not silently change posted history; each run creates a new draft/transaction.
- Failed recurrence logs error and surfaces to user.

## Acceptance criteria
- A monthly recurring payment generates on schedule and is visible in a queue/list.
- Users can pause/resume templates.
- Allocation rules apply deterministically given the same inputs.
