-- V13: Add saved views for user grid customisation
-- Supports spec 15: UI Customisation and Global Search

-- Saved view table - stores per-user grid configurations
CREATE TABLE saved_view (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    columns_json TEXT,
    filters_json TEXT,
    sort_json TEXT,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES company(id),
    FOREIGN KEY (user_id) REFERENCES app_user(id),
    UNIQUE (company_id, user_id, entity_type, name)
);

CREATE INDEX idx_saved_view_company_user ON saved_view(company_id, user_id);
CREATE INDEX idx_saved_view_entity_type ON saved_view(company_id, user_id, entity_type);
