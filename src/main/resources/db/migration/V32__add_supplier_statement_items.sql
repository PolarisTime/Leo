CREATE TABLE IF NOT EXISTS st_supplier_statement_item (
    id BIGINT PRIMARY KEY,
    statement_id BIGINT NOT NULL,
    line_no INTEGER NOT NULL,
    source_no VARCHAR(64) NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    brand VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    material VARCHAR(32) NOT NULL,
    spec VARCHAR(32) NOT NULL,
    length VARCHAR(32),
    unit VARCHAR(16) NOT NULL,
    batch_no VARCHAR(64),
    quantity INTEGER NOT NULL,
    quantity_unit VARCHAR(16) NOT NULL,
    piece_weight_ton NUMERIC(12, 3) NOT NULL,
    pieces_per_bundle INTEGER NOT NULL,
    weight_ton NUMERIC(14, 3) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_st_supplier_statement_item_head FOREIGN KEY (statement_id) REFERENCES st_supplier_statement (id)
);

CREATE INDEX IF NOT EXISTS idx_st_supplier_statement_item_head ON st_supplier_statement_item (statement_id);

INSERT INTO st_supplier_statement_item (
    id, statement_id, line_no, source_no, material_code, brand, category, material, spec, length,
    unit, batch_no, quantity, quantity_unit, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
)
SELECT
    780000000000000000 + ROW_NUMBER() OVER (ORDER BY s.id, i2.inbound_no, i.line_no),
    s.id,
    ROW_NUMBER() OVER (PARTITION BY s.id ORDER BY i2.inbound_no, i.line_no),
    i2.inbound_no,
    i.material_code,
    i.brand,
    i.category,
    i.material,
    i.spec,
    i.length,
    i.unit,
    i.batch_no,
    i.quantity,
    i.quantity_unit,
    i.piece_weight_ton,
    i.pieces_per_bundle,
    i.weight_ton,
    i.unit_price,
    i.amount
FROM st_supplier_statement s
JOIN LATERAL regexp_split_to_table(COALESCE(s.source_inbound_nos, ''), '\s*,\s*') source_inbound_no ON TRUE
JOIN po_purchase_inbound i2
    ON i2.inbound_no = BTRIM(source_inbound_no)
   AND i2.deleted_flag = FALSE
JOIN po_purchase_inbound_item i
    ON i.inbound_id = i2.id
WHERE BTRIM(source_inbound_no) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM st_supplier_statement_item existing
      WHERE existing.statement_id = s.id
  );
