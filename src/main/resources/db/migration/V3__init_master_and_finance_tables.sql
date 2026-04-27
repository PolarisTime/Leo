CREATE TABLE IF NOT EXISTS md_customer (
    id BIGINT PRIMARY KEY,
    customer_code VARCHAR(64) NOT NULL UNIQUE,
    customer_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    city VARCHAR(64) NOT NULL,
    settlement_mode VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_md_customer_name ON md_customer (customer_name);

CREATE TABLE IF NOT EXISTS md_carrier (
    id BIGINT PRIMARY KEY,
    carrier_code VARCHAR(64) NOT NULL UNIQUE,
    carrier_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    vehicle_type VARCHAR(64) NOT NULL,
    price_mode VARCHAR(32),
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_md_carrier_name ON md_carrier (carrier_name);

CREATE TABLE IF NOT EXISTS md_warehouse (
    id BIGINT PRIMARY KEY,
    warehouse_code VARCHAR(64) NOT NULL UNIQUE,
    warehouse_name VARCHAR(128) NOT NULL,
    warehouse_type VARCHAR(32) NOT NULL,
    contact_name VARCHAR(64) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    address VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_md_warehouse_name ON md_warehouse (warehouse_name);

CREATE TABLE IF NOT EXISTS fm_receipt (
    id BIGINT PRIMARY KEY,
    receipt_no VARCHAR(32) NOT NULL UNIQUE,
    customer_name VARCHAR(128) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    receipt_date DATE NOT NULL,
    pay_type VARCHAR(32) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_fm_receipt_customer_date ON fm_receipt (customer_name, receipt_date);

CREATE TABLE IF NOT EXISTS fm_payment (
    id BIGINT PRIMARY KEY,
    payment_no VARCHAR(32) NOT NULL UNIQUE,
    business_type VARCHAR(32) NOT NULL,
    counterparty_name VARCHAR(128) NOT NULL,
    payment_date DATE NOT NULL,
    pay_type VARCHAR(32) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_fm_payment_counterparty_date ON fm_payment (counterparty_name, payment_date);
