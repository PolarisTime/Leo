CREATE TABLE IF NOT EXISTS po_purchase_inbound (
    id BIGINT PRIMARY KEY,
    inbound_no VARCHAR(32) NOT NULL UNIQUE,
    purchase_order_no VARCHAR(256),
    supplier_name VARCHAR(128) NOT NULL,
    warehouse_name VARCHAR(128) NOT NULL,
    inbound_date DATE NOT NULL,
    settlement_mode VARCHAR(32) NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_po_purchase_inbound_supplier_date ON po_purchase_inbound (supplier_name, inbound_date);
CREATE INDEX IF NOT EXISTS idx_po_purchase_inbound_status ON po_purchase_inbound (status);

CREATE TABLE IF NOT EXISTS po_purchase_inbound_item (
    id BIGINT PRIMARY KEY,
    inbound_id BIGINT NOT NULL,
    line_no INTEGER NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    brand VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    material VARCHAR(32) NOT NULL,
    spec VARCHAR(32) NOT NULL,
    length VARCHAR(32),
    unit VARCHAR(16) NOT NULL,
    batch_no VARCHAR(64),
    quantity INTEGER NOT NULL,
    piece_weight_ton NUMERIC(12, 3) NOT NULL,
    pieces_per_bundle INTEGER NOT NULL,
    weight_ton NUMERIC(14, 3) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_po_purchase_inbound_item_head FOREIGN KEY (inbound_id) REFERENCES po_purchase_inbound (id)
);

CREATE INDEX IF NOT EXISTS idx_po_purchase_inbound_item_head ON po_purchase_inbound_item (inbound_id);
CREATE INDEX IF NOT EXISTS idx_po_purchase_inbound_item_batch ON po_purchase_inbound_item (batch_no);

CREATE TABLE IF NOT EXISTS so_sales_order (
    id BIGINT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL UNIQUE,
    purchase_inbound_no VARCHAR(256),
    customer_name VARCHAR(128) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    order_date DATE NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_so_sales_order_customer_project ON so_sales_order (customer_name, project_name);
CREATE INDEX IF NOT EXISTS idx_so_sales_order_status ON so_sales_order (status);

CREATE TABLE IF NOT EXISTS so_sales_order_item (
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
    CONSTRAINT fk_so_sales_order_item_head FOREIGN KEY (order_id) REFERENCES so_sales_order (id)
);

CREATE INDEX IF NOT EXISTS idx_so_sales_order_item_head ON so_sales_order_item (order_id);
CREATE INDEX IF NOT EXISTS idx_so_sales_order_item_material ON so_sales_order_item (material_code);

CREATE TABLE IF NOT EXISTS so_sales_outbound (
    id BIGINT PRIMARY KEY,
    outbound_no VARCHAR(32) NOT NULL UNIQUE,
    sales_order_no VARCHAR(256),
    customer_name VARCHAR(128) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    warehouse_name VARCHAR(128) NOT NULL,
    outbound_date DATE NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_so_sales_outbound_customer_date ON so_sales_outbound (customer_name, outbound_date);
CREATE INDEX IF NOT EXISTS idx_so_sales_outbound_status ON so_sales_outbound (status);

CREATE TABLE IF NOT EXISTS so_sales_outbound_item (
    id BIGINT PRIMARY KEY,
    outbound_id BIGINT NOT NULL,
    line_no INTEGER NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    brand VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    material VARCHAR(32) NOT NULL,
    spec VARCHAR(32) NOT NULL,
    length VARCHAR(32),
    unit VARCHAR(16) NOT NULL,
    batch_no VARCHAR(64),
    quantity INTEGER NOT NULL,
    piece_weight_ton NUMERIC(12, 3) NOT NULL,
    pieces_per_bundle INTEGER NOT NULL,
    weight_ton NUMERIC(14, 3) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_so_sales_outbound_item_head FOREIGN KEY (outbound_id) REFERENCES so_sales_outbound (id)
);

CREATE INDEX IF NOT EXISTS idx_so_sales_outbound_item_head ON so_sales_outbound_item (outbound_id);
CREATE INDEX IF NOT EXISTS idx_so_sales_outbound_item_batch ON so_sales_outbound_item (batch_no);

CREATE TABLE IF NOT EXISTS lg_freight_bill (
    id BIGINT PRIMARY KEY,
    bill_no VARCHAR(32) NOT NULL UNIQUE,
    outbound_no VARCHAR(256),
    carrier_name VARCHAR(128) NOT NULL,
    customer_name VARCHAR(128) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    bill_time DATE NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    total_weight NUMERIC(14, 3) NOT NULL,
    total_freight NUMERIC(14, 2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    delivery_status VARCHAR(16) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_carrier_date ON lg_freight_bill (carrier_name, bill_time);
CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_status ON lg_freight_bill (status, delivery_status);

CREATE TABLE IF NOT EXISTS lg_freight_bill_item (
    id BIGINT PRIMARY KEY,
    bill_id BIGINT NOT NULL,
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
    CONSTRAINT fk_lg_freight_bill_item_head FOREIGN KEY (bill_id) REFERENCES lg_freight_bill (id)
);

CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_item_bill ON lg_freight_bill_item (bill_id);
CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_item_source ON lg_freight_bill_item (source_no);
