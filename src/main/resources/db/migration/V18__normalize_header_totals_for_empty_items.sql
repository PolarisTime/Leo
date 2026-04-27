UPDATE po_purchase_order h
SET total_weight = COALESCE((
        SELECT ROUND(SUM(i.weight_ton), 3)
        FROM po_purchase_order_item i
        WHERE i.order_id = h.id
    ), 0),
    total_amount = COALESCE((
        SELECT ROUND(SUM(i.amount), 2)
        FROM po_purchase_order_item i
        WHERE i.order_id = h.id
    ), 0);

UPDATE po_purchase_inbound h
SET total_weight = COALESCE((
        SELECT ROUND(SUM(i.weight_ton), 3)
        FROM po_purchase_inbound_item i
        WHERE i.inbound_id = h.id
    ), 0),
    total_amount = COALESCE((
        SELECT ROUND(SUM(i.amount), 2)
        FROM po_purchase_inbound_item i
        WHERE i.inbound_id = h.id
    ), 0);

UPDATE so_sales_order h
SET total_weight = COALESCE((
        SELECT ROUND(SUM(i.weight_ton), 3)
        FROM so_sales_order_item i
        WHERE i.order_id = h.id
    ), 0),
    total_amount = COALESCE((
        SELECT ROUND(SUM(i.amount), 2)
        FROM so_sales_order_item i
        WHERE i.order_id = h.id
    ), 0);

UPDATE so_sales_outbound h
SET total_weight = COALESCE((
        SELECT ROUND(SUM(i.weight_ton), 3)
        FROM so_sales_outbound_item i
        WHERE i.outbound_id = h.id
    ), 0),
    total_amount = COALESCE((
        SELECT ROUND(SUM(i.amount), 2)
        FROM so_sales_outbound_item i
        WHERE i.outbound_id = h.id
    ), 0);

UPDATE lg_freight_bill h
SET total_weight = COALESCE((
        SELECT ROUND(SUM(i.weight_ton), 3)
        FROM lg_freight_bill_item i
        WHERE i.bill_id = h.id
    ), 0),
    total_freight = ROUND(
        COALESCE((
            SELECT ROUND(SUM(i.weight_ton), 3)
            FROM lg_freight_bill_item i
            WHERE i.bill_id = h.id
        ), 0) * COALESCE(h.unit_price, 0),
        2
    );

UPDATE st_freight_statement h
SET total_weight = COALESCE((
        SELECT ROUND(SUM(i.weight_ton), 3)
        FROM st_freight_statement_item i
        WHERE i.statement_id = h.id
    ), 0),
    unpaid_amount = GREATEST(COALESCE(h.total_freight, 0) - COALESCE(h.paid_amount, 0), 0);

UPDATE ct_purchase_contract h
SET total_weight = COALESCE((
        SELECT ROUND(SUM(i.weight_ton), 3)
        FROM ct_purchase_contract_item i
        WHERE i.contract_id = h.id
    ), 0),
    total_amount = COALESCE((
        SELECT ROUND(SUM(i.amount), 2)
        FROM ct_purchase_contract_item i
        WHERE i.contract_id = h.id
    ), 0);

UPDATE ct_sales_contract h
SET total_weight = COALESCE((
        SELECT ROUND(SUM(i.weight_ton), 3)
        FROM ct_sales_contract_item i
        WHERE i.contract_id = h.id
    ), 0),
    total_amount = COALESCE((
        SELECT ROUND(SUM(i.amount), 2)
        FROM ct_sales_contract_item i
        WHERE i.contract_id = h.id
    ), 0);
