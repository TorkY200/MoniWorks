-- Products table for items and services
CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    code VARCHAR(31) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    category VARCHAR(50),
    buy_price DECIMAL(19,2),
    sell_price DECIMAL(19,2),
    tax_code VARCHAR(10),
    is_inventoried BOOLEAN NOT NULL DEFAULT FALSE,
    barcode VARCHAR(50),
    image_attachment_id BIGINT,
    sticky_note VARCHAR(500),
    sales_account_id BIGINT,
    purchase_account_id BIGINT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (company_id, code),
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (sales_account_id) REFERENCES account(id),
    FOREIGN KEY (purchase_account_id) REFERENCES account(id)
);

CREATE INDEX idx_product_company ON product(company_id);
CREATE INDEX idx_product_category ON product(company_id, category);
CREATE INDEX idx_product_active ON product(company_id, active);
CREATE INDEX idx_product_barcode ON product(barcode);
