CREATE TABLE IF NOT EXISTS st_supplier_statement (
    id BIGINT PRIMARY KEY,
    statement_no VARCHAR(32) NOT NULL UNIQUE,
    source_inbound_nos VARCHAR(500),
    supplier_name VARCHAR(128) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    purchase_amount NUMERIC(14, 2) NOT NULL,
    payment_amount NUMERIC(14, 2) NOT NULL,
    closing_amount NUMERIC(14, 2) NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_st_supplier_statement_supplier_date ON st_supplier_statement (supplier_name, end_date);

CREATE TABLE IF NOT EXISTS st_customer_statement (
    id BIGINT PRIMARY KEY,
    statement_no VARCHAR(32) NOT NULL UNIQUE,
    source_order_nos VARCHAR(500),
    customer_name VARCHAR(128) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    sales_amount NUMERIC(14, 2) NOT NULL,
    receipt_amount NUMERIC(14, 2) NOT NULL,
    closing_amount NUMERIC(14, 2) NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_st_customer_statement_customer_date ON st_customer_statement (customer_name, end_date);

CREATE TABLE IF NOT EXISTS st_freight_statement (
    id BIGINT PRIMARY KEY,
    statement_no VARCHAR(32) NOT NULL UNIQUE,
    source_bill_nos VARCHAR(500),
    carrier_name VARCHAR(128) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    total_weight NUMERIC(14, 3) NOT NULL,
    total_freight NUMERIC(14, 2) NOT NULL,
    paid_amount NUMERIC(14, 2) NOT NULL,
    unpaid_amount NUMERIC(14, 2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    sign_status VARCHAR(16) NOT NULL,
    attachment VARCHAR(500),
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_st_freight_statement_carrier_date ON st_freight_statement (carrier_name, end_date);

CREATE TABLE IF NOT EXISTS st_freight_statement_item (
    id BIGINT PRIMARY KEY,
    statement_id BIGINT NOT NULL,
    line_no INTEGER NOT NULL,
    source_no VARCHAR(64) NOT NULL,
    customer_name VARCHAR(128) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    material_name VARCHAR(128),
    brand VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    material VARCHAR(32) NOT NULL,
    spec VARCHAR(32) NOT NULL,
    length VARCHAR(32),
    quantity INTEGER NOT NULL,
    piece_weight_ton NUMERIC(12, 3) NOT NULL,
    pieces_per_bundle INTEGER NOT NULL,
    batch_no VARCHAR(64),
    weight_ton NUMERIC(14, 3) NOT NULL,
    warehouse_name VARCHAR(128),
    CONSTRAINT fk_st_freight_statement_item_head FOREIGN KEY (statement_id) REFERENCES st_freight_statement (id)
);

CREATE INDEX IF NOT EXISTS idx_st_freight_statement_item_head ON st_freight_statement_item (statement_id);

CREATE TABLE IF NOT EXISTS ct_purchase_contract (
    id BIGINT PRIMARY KEY,
    contract_no VARCHAR(32) NOT NULL UNIQUE,
    supplier_name VARCHAR(128) NOT NULL,
    sign_date DATE NOT NULL,
    effective_date DATE NOT NULL,
    expire_date DATE NOT NULL,
    buyer_name VARCHAR(64) NOT NULL,
    total_weight NUMERIC(14, 3) NOT NULL,
    total_amount NUMERIC(14, 2) NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_ct_purchase_contract_supplier_date ON ct_purchase_contract (supplier_name, sign_date);
CREATE INDEX IF NOT EXISTS idx_ct_purchase_contract_status ON ct_purchase_contract (status);

CREATE TABLE IF NOT EXISTS ct_purchase_contract_item (
    id BIGINT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    line_no INTEGER NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    brand VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    material VARCHAR(32) NOT NULL,
    spec VARCHAR(32) NOT NULL,
    length VARCHAR(32),
    unit VARCHAR(16) NOT NULL,
    quantity INTEGER NOT NULL,
    piece_weight_ton NUMERIC(12, 3) NOT NULL,
    pieces_per_bundle INTEGER NOT NULL,
    weight_ton NUMERIC(14, 3) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_ct_purchase_contract_item_head FOREIGN KEY (contract_id) REFERENCES ct_purchase_contract (id)
);

CREATE INDEX IF NOT EXISTS idx_ct_purchase_contract_item_head ON ct_purchase_contract_item (contract_id);

CREATE TABLE IF NOT EXISTS ct_sales_contract (
    id BIGINT PRIMARY KEY,
    contract_no VARCHAR(32) NOT NULL UNIQUE,
    customer_name VARCHAR(128) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    sign_date DATE NOT NULL,
    effective_date DATE NOT NULL,
    expire_date DATE NOT NULL,
    sales_name VARCHAR(64) NOT NULL,
    total_weight NUMERIC(14, 3) NOT NULL,
    total_amount NUMERIC(14, 2) NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_ct_sales_contract_customer_date ON ct_sales_contract (customer_name, sign_date);
CREATE INDEX IF NOT EXISTS idx_ct_sales_contract_status ON ct_sales_contract (status);

CREATE TABLE IF NOT EXISTS ct_sales_contract_item (
    id BIGINT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    line_no INTEGER NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    brand VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    material VARCHAR(32) NOT NULL,
    spec VARCHAR(32) NOT NULL,
    length VARCHAR(32),
    unit VARCHAR(16) NOT NULL,
    quantity INTEGER NOT NULL,
    piece_weight_ton NUMERIC(12, 3) NOT NULL,
    pieces_per_bundle INTEGER NOT NULL,
    weight_ton NUMERIC(14, 3) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_ct_sales_contract_item_head FOREIGN KEY (contract_id) REFERENCES ct_sales_contract (id)
);

CREATE INDEX IF NOT EXISTS idx_ct_sales_contract_item_head ON ct_sales_contract_item (contract_id);
