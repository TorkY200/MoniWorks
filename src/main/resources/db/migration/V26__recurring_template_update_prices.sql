-- V26: Add updatePricesOnExecution flag to recurring templates
-- This enables templates to fetch current product prices/descriptions when executing
-- rather than using the frozen values from template creation

ALTER TABLE recurring_template ADD COLUMN update_prices_on_execution BOOLEAN DEFAULT FALSE NOT NULL;

COMMENT ON COLUMN recurring_template.update_prices_on_execution IS 'When true, fetch current product prices and descriptions when executing invoice/bill templates';
