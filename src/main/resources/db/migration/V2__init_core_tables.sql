CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY,
    login_name VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    user_name VARCHAR(64) NOT NULL,
    mobile VARCHAR(32),
    role_name VARCHAR(64),
    data_scope VARCHAR(32),
    permission_summary VARCHAR(500),
    last_login_date TIMESTAMP,
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

CREATE TABLE IF NOT EXISTS auth_refresh_token (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_id VARCHAR(64) NOT NULL UNIQUE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    login_ip VARCHAR(64),
    device_info VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_user_id ON auth_refresh_token (user_id);

CREATE TABLE IF NOT EXISTS md_material (
    id BIGINT PRIMARY KEY,
    material_code VARCHAR(64) NOT NULL UNIQUE,
    brand VARCHAR(64) NOT NULL,
    material VARCHAR(32) NOT NULL,
    category VARCHAR(32) NOT NULL,
    spec VARCHAR(32) NOT NULL,
    length VARCHAR(32),
    unit VARCHAR(16) NOT NULL,
    piece_weight_ton NUMERIC(12, 3) NOT NULL,
    pieces_per_bundle INTEGER NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_md_material_category ON md_material (category);
CREATE INDEX IF NOT EXISTS idx_md_material_brand ON md_material (brand);

CREATE TABLE IF NOT EXISTS md_supplier (
    id BIGINT PRIMARY KEY,
    supplier_code VARCHAR(64) NOT NULL UNIQUE,
    supplier_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    city VARCHAR(64) NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_md_supplier_name ON md_supplier (supplier_name);

CREATE TABLE IF NOT EXISTS po_purchase_order (
    id BIGINT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL UNIQUE,
    supplier_name VARCHAR(128) NOT NULL,
    order_date DATE NOT NULL,
    buyer_name VARCHAR(64),
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

CREATE INDEX IF NOT EXISTS idx_po_purchase_order_supplier_date ON po_purchase_order (supplier_name, order_date);
CREATE INDEX IF NOT EXISTS idx_po_purchase_order_status ON po_purchase_order (status);

CREATE TABLE IF NOT EXISTS po_purchase_order_item (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
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
    CONSTRAINT fk_po_purchase_order_item_order FOREIGN KEY (order_id) REFERENCES po_purchase_order (id)
);

CREATE INDEX IF NOT EXISTS idx_po_purchase_order_item_order_id ON po_purchase_order_item (order_id);
CREATE INDEX IF NOT EXISTS idx_po_purchase_order_item_material_code ON po_purchase_order_item (material_code);
