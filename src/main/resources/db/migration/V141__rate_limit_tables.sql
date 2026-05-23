-- V141: Rate limiting rule persistence table
-- Phase 3: PostgreSQL-backed dynamic rate limit configuration

CREATE TABLE IF NOT EXISTS sys_rate_limit_rule (
    id BIGINT PRIMARY KEY,
    rule_key VARCHAR(128) NOT NULL,
    rule_type VARCHAR(20) NOT NULL,            -- GLOBAL / METHOD / API_KEY
    rate DECIMAL(10,2) NOT NULL DEFAULT 10,    -- tokens/sec
    capacity INT NOT NULL DEFAULT 20,          -- burst
    tokens_per_request INT NOT NULL DEFAULT 1,
    priority SMALLINT NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_rate_limit_rule_key
    ON sys_rate_limit_rule (rule_key, rule_type) WHERE enabled = TRUE;

-- Seed default rules
INSERT INTO sys_rate_limit_rule (id, rule_key, rule_type, rate, capacity, priority)
VALUES (1, 'global_default', 'GLOBAL', 100, 150, 200)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_rate_limit_rule (id, rule_key, rule_type, rate, capacity, priority)
VALUES (2, 'AuthController.login', 'METHOD', 0.1, 5, 10)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_rate_limit_rule (id, rule_key, rule_type, rate, capacity, priority)
VALUES (3, 'AttachmentController.upload', 'METHOD', 0.5, 10, 20)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_rate_limit_rule (id, rule_key, rule_type, rate, capacity, priority)
VALUES (4, 'DatabaseBackupController', 'METHOD', 0.05, 1, 30)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_rate_limit_rule (id, rule_key, rule_type, rate, capacity, priority)
VALUES (5, 'api_key:gold', 'API_KEY', 1000, 2000, 5)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_rate_limit_rule (id, rule_key, rule_type, rate, capacity, priority)
VALUES (6, 'api_key:standard', 'API_KEY', 100, 150, 10)
ON CONFLICT (id) DO NOTHING;
