ALTER TABLE md_material ADD COLUMN IF NOT EXISTS quantity_unit VARCHAR(16);

ALTER TABLE po_purchase_order_item ADD COLUMN IF NOT EXISTS quantity_unit VARCHAR(16);
ALTER TABLE po_purchase_inbound_item ADD COLUMN IF NOT EXISTS quantity_unit VARCHAR(16);
ALTER TABLE so_sales_order_item ADD COLUMN IF NOT EXISTS quantity_unit VARCHAR(16);
ALTER TABLE so_sales_outbound_item ADD COLUMN IF NOT EXISTS quantity_unit VARCHAR(16);
ALTER TABLE lg_freight_bill_item ADD COLUMN IF NOT EXISTS quantity_unit VARCHAR(16);
ALTER TABLE st_freight_statement_item ADD COLUMN IF NOT EXISTS quantity_unit VARCHAR(16);
ALTER TABLE ct_purchase_contract_item ADD COLUMN IF NOT EXISTS quantity_unit VARCHAR(16);
ALTER TABLE ct_sales_contract_item ADD COLUMN IF NOT EXISTS quantity_unit VARCHAR(16);

UPDATE md_material SET quantity_unit = '件' WHERE COALESCE(BTRIM(quantity_unit), '') <> '件';

UPDATE po_purchase_order_item SET quantity_unit = '件' WHERE COALESCE(BTRIM(quantity_unit), '') <> '件';
UPDATE po_purchase_inbound_item SET quantity_unit = '件' WHERE COALESCE(BTRIM(quantity_unit), '') <> '件';
UPDATE so_sales_order_item SET quantity_unit = '件' WHERE COALESCE(BTRIM(quantity_unit), '') <> '件';
UPDATE so_sales_outbound_item SET quantity_unit = '件' WHERE COALESCE(BTRIM(quantity_unit), '') <> '件';
UPDATE lg_freight_bill_item SET quantity_unit = '件' WHERE COALESCE(BTRIM(quantity_unit), '') <> '件';
UPDATE st_freight_statement_item SET quantity_unit = '件' WHERE COALESCE(BTRIM(quantity_unit), '') <> '件';
UPDATE ct_purchase_contract_item SET quantity_unit = '件' WHERE COALESCE(BTRIM(quantity_unit), '') <> '件';
UPDATE ct_sales_contract_item SET quantity_unit = '件' WHERE COALESCE(BTRIM(quantity_unit), '') <> '件';

ALTER TABLE md_material ALTER COLUMN quantity_unit SET DEFAULT '件';
ALTER TABLE md_material ALTER COLUMN quantity_unit SET NOT NULL;

ALTER TABLE po_purchase_order_item ALTER COLUMN quantity_unit SET DEFAULT '件';
ALTER TABLE po_purchase_order_item ALTER COLUMN quantity_unit SET NOT NULL;
ALTER TABLE po_purchase_inbound_item ALTER COLUMN quantity_unit SET DEFAULT '件';
ALTER TABLE po_purchase_inbound_item ALTER COLUMN quantity_unit SET NOT NULL;
ALTER TABLE so_sales_order_item ALTER COLUMN quantity_unit SET DEFAULT '件';
ALTER TABLE so_sales_order_item ALTER COLUMN quantity_unit SET NOT NULL;
ALTER TABLE so_sales_outbound_item ALTER COLUMN quantity_unit SET DEFAULT '件';
ALTER TABLE so_sales_outbound_item ALTER COLUMN quantity_unit SET NOT NULL;
ALTER TABLE lg_freight_bill_item ALTER COLUMN quantity_unit SET DEFAULT '件';
ALTER TABLE lg_freight_bill_item ALTER COLUMN quantity_unit SET NOT NULL;
ALTER TABLE st_freight_statement_item ALTER COLUMN quantity_unit SET DEFAULT '件';
ALTER TABLE st_freight_statement_item ALTER COLUMN quantity_unit SET NOT NULL;
ALTER TABLE ct_purchase_contract_item ALTER COLUMN quantity_unit SET DEFAULT '件';
ALTER TABLE ct_purchase_contract_item ALTER COLUMN quantity_unit SET NOT NULL;
ALTER TABLE ct_sales_contract_item ALTER COLUMN quantity_unit SET DEFAULT '件';
ALTER TABLE ct_sales_contract_item ALTER COLUMN quantity_unit SET NOT NULL;

UPDATE po_purchase_order_item
SET weight_ton = ROUND(quantity * piece_weight_ton, 3),
    amount = ROUND(quantity * piece_weight_ton * unit_price, 2);

UPDATE po_purchase_inbound_item
SET weight_ton = ROUND(quantity * piece_weight_ton, 3),
    amount = ROUND(quantity * piece_weight_ton * unit_price, 2);

UPDATE so_sales_order_item
SET weight_ton = ROUND(quantity * piece_weight_ton, 3),
    amount = ROUND(quantity * piece_weight_ton * unit_price, 2);

UPDATE so_sales_outbound_item
SET weight_ton = ROUND(quantity * piece_weight_ton, 3),
    amount = ROUND(quantity * piece_weight_ton * unit_price, 2);

UPDATE lg_freight_bill_item
SET weight_ton = ROUND(quantity * piece_weight_ton, 3);

UPDATE st_freight_statement_item
SET weight_ton = ROUND(quantity * piece_weight_ton, 3);

UPDATE ct_purchase_contract_item
SET weight_ton = ROUND(quantity * piece_weight_ton, 3),
    amount = ROUND(quantity * piece_weight_ton * unit_price, 2);

UPDATE ct_sales_contract_item
SET weight_ton = ROUND(quantity * piece_weight_ton, 3),
    amount = ROUND(quantity * piece_weight_ton * unit_price, 2);

UPDATE po_purchase_order h
SET total_weight = agg.total_weight,
    total_amount = agg.total_amount
FROM (
    SELECT order_id, ROUND(SUM(weight_ton), 3) AS total_weight, ROUND(SUM(amount), 2) AS total_amount
    FROM po_purchase_order_item
    GROUP BY order_id
) agg
WHERE h.id = agg.order_id;

UPDATE po_purchase_inbound h
SET total_weight = agg.total_weight,
    total_amount = agg.total_amount
FROM (
    SELECT inbound_id, ROUND(SUM(weight_ton), 3) AS total_weight, ROUND(SUM(amount), 2) AS total_amount
    FROM po_purchase_inbound_item
    GROUP BY inbound_id
) agg
WHERE h.id = agg.inbound_id;

UPDATE so_sales_order h
SET total_weight = agg.total_weight,
    total_amount = agg.total_amount
FROM (
    SELECT order_id, ROUND(SUM(weight_ton), 3) AS total_weight, ROUND(SUM(amount), 2) AS total_amount
    FROM so_sales_order_item
    GROUP BY order_id
) agg
WHERE h.id = agg.order_id;

UPDATE so_sales_outbound h
SET total_weight = agg.total_weight,
    total_amount = agg.total_amount
FROM (
    SELECT outbound_id, ROUND(SUM(weight_ton), 3) AS total_weight, ROUND(SUM(amount), 2) AS total_amount
    FROM so_sales_outbound_item
    GROUP BY outbound_id
) agg
WHERE h.id = agg.outbound_id;

UPDATE lg_freight_bill h
SET total_weight = agg.total_weight,
    total_freight = ROUND(agg.total_weight * h.unit_price, 2)
FROM (
    SELECT bill_id, ROUND(SUM(weight_ton), 3) AS total_weight
    FROM lg_freight_bill_item
    GROUP BY bill_id
) agg
WHERE h.id = agg.bill_id;

UPDATE st_freight_statement h
SET total_weight = agg.total_weight,
    unpaid_amount = GREATEST(total_freight - paid_amount, 0)
FROM (
    SELECT statement_id, ROUND(SUM(weight_ton), 3) AS total_weight
    FROM st_freight_statement_item
    GROUP BY statement_id
) agg
WHERE h.id = agg.statement_id;

UPDATE ct_purchase_contract h
SET total_weight = agg.total_weight,
    total_amount = agg.total_amount
FROM (
    SELECT contract_id, ROUND(SUM(weight_ton), 3) AS total_weight, ROUND(SUM(amount), 2) AS total_amount
    FROM ct_purchase_contract_item
    GROUP BY contract_id
) agg
WHERE h.id = agg.contract_id;

UPDATE ct_sales_contract h
SET total_weight = agg.total_weight,
    total_amount = agg.total_amount
FROM (
    SELECT contract_id, ROUND(SUM(weight_ton), 3) AS total_weight, ROUND(SUM(amount), 2) AS total_amount
    FROM ct_sales_contract_item
    GROUP BY contract_id
) agg
WHERE h.id = agg.contract_id;
