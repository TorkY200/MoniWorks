-- Add debit note support to supplier_bill table
ALTER TABLE supplier_bill ADD COLUMN bill_type VARCHAR(15) DEFAULT 'BILL' NOT NULL;
ALTER TABLE supplier_bill ADD COLUMN original_bill_id BIGINT;

-- Add foreign key for debit note -> original bill relationship
ALTER TABLE supplier_bill ADD CONSTRAINT fk_bill_original_bill
    FOREIGN KEY (original_bill_id) REFERENCES supplier_bill(id);

-- Index for finding debit notes by original bill
CREATE INDEX idx_bill_original ON supplier_bill(original_bill_id);

-- Index for filtering by bill type
CREATE INDEX idx_bill_type ON supplier_bill(bill_type);
