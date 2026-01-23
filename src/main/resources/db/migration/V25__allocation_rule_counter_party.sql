-- V25: Add counter-party pattern matching to allocation rules
-- Per spec 05: "rules can match on description, amount ranges, counterparty, etc."

ALTER TABLE allocation_rule ADD COLUMN counter_party_pattern VARCHAR(200);

COMMENT ON COLUMN allocation_rule.counter_party_pattern IS 'Optional counter-party/payee pattern for matching (case-insensitive contains)';
