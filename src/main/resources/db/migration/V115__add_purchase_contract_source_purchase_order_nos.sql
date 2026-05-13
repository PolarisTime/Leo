ALTER TABLE ct_purchase_contract
    ADD COLUMN IF NOT EXISTS source_purchase_order_nos VARCHAR(512);

CREATE INDEX IF NOT EXISTS idx_ct_purchase_contract_source_purchase_order_nos_trgm
    ON ct_purchase_contract USING gin (source_purchase_order_nos gin_trgm_ops);

UPDATE ct_purchase_contract
SET source_purchase_order_nos = '2026PO000003'
WHERE contract_no = '2026PC000002'
  AND COALESCE(BTRIM(source_purchase_order_nos), '') = '';
