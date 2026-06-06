DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM fm_receipt_allocation
        WHERE receipt_id IS NOT NULL
          AND source_statement_id IS NOT NULL
        GROUP BY receipt_id, source_statement_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate fm_receipt_allocation receipt_id/source_statement_id rows must be cleaned before V173';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM fm_payment_allocation
        WHERE payment_id IS NOT NULL
          AND source_statement_id IS NOT NULL
        GROUP BY payment_id, source_statement_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate fm_payment_allocation payment_id/source_statement_id rows must be cleaned before V173';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM st_customer_statement_item
        WHERE source_sales_order_item_id IS NOT NULL
        GROUP BY statement_id, source_sales_order_item_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate st_customer_statement_item statement_id/source_sales_order_item_id rows must be cleaned before V173';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM st_supplier_statement_item
        WHERE source_inbound_item_id IS NOT NULL
        GROUP BY statement_id, source_inbound_item_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate st_supplier_statement_item statement_id/source_inbound_item_id rows must be cleaned before V173';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_fm_receipt_allocation_receipt_statement
    ON fm_receipt_allocation (receipt_id, source_statement_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_fm_payment_allocation_payment_statement
    ON fm_payment_allocation (payment_id, source_statement_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_st_customer_statement_item_source_line
    ON st_customer_statement_item (statement_id, source_sales_order_item_id)
    WHERE source_sales_order_item_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_st_supplier_statement_item_source_line
    ON st_supplier_statement_item (statement_id, source_inbound_item_id)
    WHERE source_inbound_item_id IS NOT NULL;
