-- User Security Level: Stores the maximum security level each user can access per company
-- Accounts with security_level > user's max_security_level will be hidden from that user

CREATE TABLE user_security_level (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    max_level INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_usl_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_usl_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT uq_user_company_security UNIQUE (user_id, company_id)
);

CREATE INDEX idx_usl_user ON user_security_level(user_id);
CREATE INDEX idx_usl_company ON user_security_level(company_id);

-- Add index on account security_level for efficient filtering
CREATE INDEX idx_account_security_level ON account(company_id, security_level);
