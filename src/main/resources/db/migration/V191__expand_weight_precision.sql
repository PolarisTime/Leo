ALTER TABLE md_material
    ALTER COLUMN piece_weight_ton TYPE NUMERIC(18, 8);

ALTER TABLE po_purchase_order
    ALTER COLUMN total_weight TYPE NUMERIC(18, 8);

ALTER TABLE po_purchase_order_item
    ALTER COLUMN piece_weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN actual_weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN actual_piece_weight_ton TYPE NUMERIC(18, 8);

ALTER TABLE po_purchase_order_item_piece_weight
    ALTER COLUMN weight_ton TYPE NUMERIC(18, 8);

ALTER TABLE po_purchase_inbound
    ALTER COLUMN total_weight TYPE NUMERIC(18, 8);

ALTER TABLE po_purchase_inbound_item
    ALTER COLUMN piece_weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weigh_weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weight_adjustment_ton TYPE NUMERIC(18, 8);

ALTER TABLE so_sales_order
    ALTER COLUMN total_weight TYPE NUMERIC(18, 8);

ALTER TABLE so_sales_order_item
    ALTER COLUMN piece_weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN original_weight_ton TYPE NUMERIC(18, 8);

ALTER TABLE so_sales_outbound
    ALTER COLUMN total_weight TYPE NUMERIC(18, 8);

ALTER TABLE so_sales_outbound_item
    ALTER COLUMN piece_weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weight_ton TYPE NUMERIC(18, 8);

ALTER TABLE lg_freight_bill
    ALTER COLUMN total_weight TYPE NUMERIC(18, 8);

ALTER TABLE lg_freight_bill_item
    ALTER COLUMN piece_weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weight_ton TYPE NUMERIC(18, 8);

ALTER TABLE ct_purchase_contract
    ALTER COLUMN total_weight TYPE NUMERIC(18, 8);

ALTER TABLE ct_purchase_contract_item
    ALTER COLUMN piece_weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weight_ton TYPE NUMERIC(18, 8);

ALTER TABLE ct_sales_contract
    ALTER COLUMN total_weight TYPE NUMERIC(18, 8);

ALTER TABLE ct_sales_contract_item
    ALTER COLUMN piece_weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weight_ton TYPE NUMERIC(18, 8);

ALTER TABLE fm_invoice_receipt_item
    ALTER COLUMN piece_weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weight_ton TYPE NUMERIC(18, 8);

ALTER TABLE fm_invoice_issue_item
    ALTER COLUMN piece_weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weight_ton TYPE NUMERIC(18, 8);

ALTER TABLE st_supplier_statement_item
    ALTER COLUMN piece_weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weigh_weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weight_adjustment_ton TYPE NUMERIC(18, 8);

ALTER TABLE st_customer_statement_item
    ALTER COLUMN piece_weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weight_ton TYPE NUMERIC(18, 8);

ALTER TABLE st_freight_statement
    ALTER COLUMN total_weight TYPE NUMERIC(18, 8);

ALTER TABLE st_freight_statement_item
    ALTER COLUMN piece_weight_ton TYPE NUMERIC(18, 8),
    ALTER COLUMN weight_ton TYPE NUMERIC(18, 8);
