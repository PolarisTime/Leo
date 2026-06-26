CREATE OR REPLACE VIEW global_search_document AS
WITH freight_bill_sources AS (
    SELECT
        item.bill_id AS record_id,
        string_agg(DISTINCT BTRIM(item.source_no), ', ' ORDER BY BTRIM(item.source_no)) AS source_nos
    FROM lg_freight_bill_item item
    WHERE COALESCE(BTRIM(item.source_no), '') <> ''
    GROUP BY item.bill_id
),
supplier_statement_sources AS (
    SELECT
        item.statement_id AS record_id,
        string_agg(DISTINCT BTRIM(item.source_no), ', ' ORDER BY BTRIM(item.source_no)) AS source_nos
    FROM st_supplier_statement_item item
    WHERE COALESCE(BTRIM(item.source_no), '') <> ''
    GROUP BY item.statement_id
),
customer_statement_sources AS (
    SELECT
        item.statement_id AS record_id,
        string_agg(DISTINCT BTRIM(item.source_no), ', ' ORDER BY BTRIM(item.source_no)) AS source_nos
    FROM st_customer_statement_item item
    WHERE COALESCE(BTRIM(item.source_no), '') <> ''
    GROUP BY item.statement_id
),
freight_statement_sources AS (
    SELECT
        item.statement_id AS record_id,
        string_agg(DISTINCT BTRIM(item.source_no), ', ' ORDER BY BTRIM(item.source_no)) AS source_nos
    FROM st_freight_statement_item item
    WHERE COALESCE(BTRIM(item.source_no), '') <> ''
    GROUP BY item.statement_id
),
purchase_contract_sources AS (
    SELECT
        relation.contract_id AS record_id,
        string_agg(DISTINCT BTRIM(purchase_order.order_no), ', ' ORDER BY BTRIM(purchase_order.order_no)) AS source_nos
    FROM ct_contract_purchase_order relation
    JOIN po_purchase_order purchase_order
      ON purchase_order.id = relation.purchase_order_id
     AND purchase_order.deleted_flag = FALSE
    WHERE COALESCE(BTRIM(purchase_order.order_no), '') <> ''
    GROUP BY relation.contract_id
),
invoice_receipt_sources AS (
    SELECT
        source_no.record_id,
        string_agg(DISTINCT source_no.value, ', ' ORDER BY source_no.value) AS source_nos
    FROM (
        SELECT
            relation.receipt_id AS record_id,
            BTRIM(purchase_order.order_no) AS value
        FROM fm_invoice_receipt_source_order relation
        JOIN po_purchase_order purchase_order
          ON purchase_order.id = relation.purchase_order_id
         AND purchase_order.deleted_flag = FALSE
        UNION ALL
        SELECT
            item.receipt_id AS record_id,
            BTRIM(item.source_no) AS value
        FROM fm_invoice_receipt_item item
    ) source_no
    WHERE COALESCE(source_no.value, '') <> ''
    GROUP BY source_no.record_id
),
invoice_issue_sources AS (
    SELECT
        source_no.record_id,
        string_agg(DISTINCT source_no.value, ', ' ORDER BY source_no.value) AS source_nos
    FROM (
        SELECT
            relation.issue_id AS record_id,
            BTRIM(sales_order.order_no) AS value
        FROM fm_invoice_issue_source_order relation
        JOIN so_sales_order sales_order
          ON sales_order.id = relation.sales_order_id
         AND sales_order.deleted_flag = FALSE
        UNION ALL
        SELECT
            item.issue_id AS record_id,
            BTRIM(item.source_no) AS value
        FROM fm_invoice_issue_item item
    ) source_no
    WHERE COALESCE(source_no.value, '') <> ''
    GROUP BY source_no.record_id
)
SELECT
    'purchase-order'::text AS module_key,
    purchase_order.id AS record_id,
    purchase_order.order_no::text AS primary_no,
    concat_ws(' / ',
        NULLIF(BTRIM(purchase_order.supplier_name), ''),
        NULLIF(BTRIM(purchase_order.buyer_name), ''),
        NULLIF(BTRIM(purchase_order.status), '')
    ) AS summary,
    concat_ws(' ',
        purchase_order.id::text,
        purchase_order.order_no,
        purchase_order.supplier_name,
        purchase_order.buyer_name,
        purchase_order.status,
        purchase_order.remark
    ) AS search_text,
    purchase_order.created_by,
    COALESCE(purchase_order.updated_at, purchase_order.created_at) AS updated_at,
    purchase_order.deleted_flag
FROM po_purchase_order purchase_order
UNION ALL
SELECT
    'purchase-inbound'::text AS module_key,
    inbound.id AS record_id,
    inbound.inbound_no::text AS primary_no,
    concat_ws(' / ',
        NULLIF(BTRIM(inbound.supplier_name), ''),
        NULLIF(BTRIM(inbound.warehouse_name), ''),
        NULLIF(BTRIM(inbound.status), '')
    ) AS summary,
    concat_ws(' ',
        inbound.id::text,
        inbound.inbound_no,
        inbound.purchase_order_no,
        inbound.supplier_name,
        inbound.warehouse_name,
        inbound.settlement_mode,
        inbound.status,
        inbound.remark
    ) AS search_text,
    inbound.created_by,
    COALESCE(inbound.updated_at, inbound.created_at) AS updated_at,
    inbound.deleted_flag
FROM po_purchase_inbound inbound
UNION ALL
SELECT
    'sales-order'::text AS module_key,
    sales_order.id AS record_id,
    sales_order.order_no::text AS primary_no,
    concat_ws(' / ',
        NULLIF(BTRIM(sales_order.customer_name), ''),
        NULLIF(BTRIM(sales_order.project_name), ''),
        NULLIF(BTRIM(sales_order.status), '')
    ) AS summary,
    concat_ws(' ',
        sales_order.id::text,
        sales_order.order_no,
        sales_order.purchase_order_no,
        sales_order.purchase_inbound_no,
        sales_order.customer_code,
        sales_order.customer_name,
        sales_order.project_id::text,
        sales_order.project_name,
        sales_order.sales_name,
        sales_order.status,
        sales_order.remark
    ) AS search_text,
    sales_order.created_by,
    COALESCE(sales_order.updated_at, sales_order.created_at) AS updated_at,
    sales_order.deleted_flag
FROM so_sales_order sales_order
UNION ALL
SELECT
    'sales-outbound'::text AS module_key,
    outbound.id AS record_id,
    outbound.outbound_no::text AS primary_no,
    concat_ws(' / ',
        NULLIF(BTRIM(outbound.customer_name), ''),
        NULLIF(BTRIM(outbound.project_name), ''),
        NULLIF(BTRIM(outbound.status), '')
    ) AS summary,
    concat_ws(' ',
        outbound.id::text,
        outbound.outbound_no,
        outbound.sales_order_no,
        outbound.customer_name,
        outbound.project_name,
        outbound.warehouse_name,
        outbound.status,
        outbound.remark
    ) AS search_text,
    outbound.created_by,
    COALESCE(outbound.updated_at, outbound.created_at) AS updated_at,
    outbound.deleted_flag
FROM so_sales_outbound outbound
UNION ALL
SELECT
    'freight-bill'::text AS module_key,
    freight_bill.id AS record_id,
    freight_bill.bill_no::text AS primary_no,
    concat_ws(' / ',
        NULLIF(BTRIM(freight_bill.carrier_name), ''),
        NULLIF(BTRIM(freight_bill.customer_name), ''),
        NULLIF(BTRIM(freight_bill.status), '')
    ) AS summary,
    concat_ws(' ',
        freight_bill.id::text,
        freight_bill.bill_no,
        freight_bill_sources.source_nos,
        freight_bill.carrier_name,
        freight_bill.vehicle_plate,
        freight_bill.customer_name,
        freight_bill.project_name,
        freight_bill.status,
        freight_bill.remark
    ) AS search_text,
    freight_bill.created_by,
    COALESCE(freight_bill.updated_at, freight_bill.created_at) AS updated_at,
    freight_bill.deleted_flag
FROM lg_freight_bill freight_bill
LEFT JOIN freight_bill_sources
  ON freight_bill_sources.record_id = freight_bill.id
UNION ALL
SELECT
    'purchase-contract'::text AS module_key,
    purchase_contract.id AS record_id,
    purchase_contract.contract_no::text AS primary_no,
    concat_ws(' / ',
        NULLIF(BTRIM(purchase_contract.supplier_name), ''),
        NULLIF(BTRIM(purchase_contract.buyer_name), ''),
        NULLIF(BTRIM(purchase_contract.status), '')
    ) AS summary,
    concat_ws(' ',
        purchase_contract.id::text,
        purchase_contract.contract_no,
        purchase_contract_sources.source_nos,
        purchase_contract.supplier_name,
        purchase_contract.buyer_name,
        purchase_contract.status,
        purchase_contract.remark
    ) AS search_text,
    purchase_contract.created_by,
    COALESCE(purchase_contract.updated_at, purchase_contract.created_at) AS updated_at,
    purchase_contract.deleted_flag
FROM ct_purchase_contract purchase_contract
LEFT JOIN purchase_contract_sources
  ON purchase_contract_sources.record_id = purchase_contract.id
UNION ALL
SELECT
    'sales-contract'::text AS module_key,
    sales_contract.id AS record_id,
    sales_contract.contract_no::text AS primary_no,
    concat_ws(' / ',
        NULLIF(BTRIM(sales_contract.customer_name), ''),
        NULLIF(BTRIM(sales_contract.project_name), ''),
        NULLIF(BTRIM(sales_contract.status), '')
    ) AS summary,
    concat_ws(' ',
        sales_contract.id::text,
        sales_contract.contract_no,
        sales_contract.customer_name,
        sales_contract.project_name,
        sales_contract.sales_name,
        sales_contract.status,
        sales_contract.remark
    ) AS search_text,
    sales_contract.created_by,
    COALESCE(sales_contract.updated_at, sales_contract.created_at) AS updated_at,
    sales_contract.deleted_flag
FROM ct_sales_contract sales_contract
UNION ALL
SELECT
    'supplier-statement'::text AS module_key,
    supplier_statement.id AS record_id,
    supplier_statement.statement_no::text AS primary_no,
    concat_ws(' / ',
        NULLIF(BTRIM(supplier_statement.supplier_name), ''),
        NULLIF(BTRIM(supplier_statement.status), '')
    ) AS summary,
    concat_ws(' ',
        supplier_statement.id::text,
        supplier_statement.statement_no,
        supplier_statement_sources.source_nos,
        supplier_statement.supplier_code,
        supplier_statement.supplier_name,
        supplier_statement.status,
        supplier_statement.remark
    ) AS search_text,
    supplier_statement.created_by,
    COALESCE(supplier_statement.updated_at, supplier_statement.created_at) AS updated_at,
    supplier_statement.deleted_flag
FROM st_supplier_statement supplier_statement
LEFT JOIN supplier_statement_sources
  ON supplier_statement_sources.record_id = supplier_statement.id
UNION ALL
SELECT
    'customer-statement'::text AS module_key,
    customer_statement.id AS record_id,
    customer_statement.statement_no::text AS primary_no,
    concat_ws(' / ',
        NULLIF(BTRIM(customer_statement.customer_name), ''),
        NULLIF(BTRIM(customer_statement.project_name), ''),
        NULLIF(BTRIM(customer_statement.status), '')
    ) AS summary,
    concat_ws(' ',
        customer_statement.id::text,
        customer_statement.statement_no,
        customer_statement_sources.source_nos,
        customer_statement.customer_code,
        customer_statement.customer_name,
        customer_statement.project_id::text,
        customer_statement.project_name,
        customer_statement.status,
        customer_statement.remark
    ) AS search_text,
    customer_statement.created_by,
    COALESCE(customer_statement.updated_at, customer_statement.created_at) AS updated_at,
    customer_statement.deleted_flag
FROM st_customer_statement customer_statement
LEFT JOIN customer_statement_sources
  ON customer_statement_sources.record_id = customer_statement.id
UNION ALL
SELECT
    'freight-statement'::text AS module_key,
    freight_statement.id AS record_id,
    freight_statement.statement_no::text AS primary_no,
    concat_ws(' / ',
        NULLIF(BTRIM(freight_statement.carrier_name), ''),
        NULLIF(BTRIM(freight_statement.sign_status), ''),
        NULLIF(BTRIM(freight_statement.status), '')
    ) AS summary,
    concat_ws(' ',
        freight_statement.id::text,
        freight_statement.statement_no,
        freight_statement_sources.source_nos,
        freight_statement.carrier_code,
        freight_statement.carrier_name,
        freight_statement.sign_status,
        freight_statement.status,
        freight_statement.remark
    ) AS search_text,
    freight_statement.created_by,
    COALESCE(freight_statement.updated_at, freight_statement.created_at) AS updated_at,
    freight_statement.deleted_flag
FROM st_freight_statement freight_statement
LEFT JOIN freight_statement_sources
  ON freight_statement_sources.record_id = freight_statement.id
UNION ALL
SELECT
    'receipt'::text AS module_key,
    receipt.id AS record_id,
    receipt.receipt_no::text AS primary_no,
    concat_ws(' / ',
        NULLIF(BTRIM(receipt.customer_name), ''),
        NULLIF(BTRIM(receipt.project_name), ''),
        NULLIF(BTRIM(receipt.status), '')
    ) AS summary,
    concat_ws(' ',
        receipt.id::text,
        receipt.receipt_no,
        receipt.customer_code,
        receipt.customer_name,
        receipt.project_id::text,
        receipt.project_name,
        receipt.pay_type,
        receipt.operator_name,
        receipt.status,
        receipt.remark
    ) AS search_text,
    receipt.created_by,
    COALESCE(receipt.updated_at, receipt.created_at) AS updated_at,
    receipt.deleted_flag
FROM fm_receipt receipt
UNION ALL
SELECT
    'payment'::text AS module_key,
    payment.id AS record_id,
    payment.payment_no::text AS primary_no,
    concat_ws(' / ',
        NULLIF(BTRIM(payment.counterparty_name), ''),
        NULLIF(BTRIM(payment.business_type), ''),
        NULLIF(BTRIM(payment.status), '')
    ) AS summary,
    concat_ws(' ',
        payment.id::text,
        payment.payment_no,
        payment.business_type,
        payment.counterparty_code,
        payment.counterparty_name,
        payment.pay_type,
        payment.operator_name,
        payment.status,
        payment.remark
    ) AS search_text,
    payment.created_by,
    COALESCE(payment.updated_at, payment.created_at) AS updated_at,
    payment.deleted_flag
FROM fm_payment payment
UNION ALL
SELECT
    'invoice-receipt'::text AS module_key,
    invoice_receipt.id AS record_id,
    invoice_receipt.receive_no::text AS primary_no,
    concat_ws(' / ',
        NULLIF(BTRIM(invoice_receipt.supplier_name), ''),
        NULLIF(BTRIM(invoice_receipt.invoice_no), ''),
        NULLIF(BTRIM(invoice_receipt.status), '')
    ) AS summary,
    concat_ws(' ',
        invoice_receipt.id::text,
        invoice_receipt.receive_no,
        invoice_receipt.invoice_no,
        invoice_receipt_sources.source_nos,
        invoice_receipt.supplier_name,
        invoice_receipt.invoice_title,
        invoice_receipt.invoice_type,
        invoice_receipt.operator_name,
        invoice_receipt.status,
        invoice_receipt.remark
    ) AS search_text,
    invoice_receipt.created_by,
    COALESCE(invoice_receipt.updated_at, invoice_receipt.created_at) AS updated_at,
    invoice_receipt.deleted_flag
FROM fm_invoice_receipt invoice_receipt
LEFT JOIN invoice_receipt_sources
  ON invoice_receipt_sources.record_id = invoice_receipt.id
UNION ALL
SELECT
    'invoice-issue'::text AS module_key,
    invoice_issue.id AS record_id,
    invoice_issue.issue_no::text AS primary_no,
    concat_ws(' / ',
        NULLIF(BTRIM(invoice_issue.customer_name), ''),
        NULLIF(BTRIM(invoice_issue.project_name), ''),
        NULLIF(BTRIM(invoice_issue.status), '')
    ) AS summary,
    concat_ws(' ',
        invoice_issue.id::text,
        invoice_issue.issue_no,
        invoice_issue.invoice_no,
        invoice_issue_sources.source_nos,
        invoice_issue.customer_name,
        invoice_issue.project_name,
        invoice_issue.invoice_type,
        invoice_issue.operator_name,
        invoice_issue.status,
        invoice_issue.remark
    ) AS search_text,
    invoice_issue.created_by,
    COALESCE(invoice_issue.updated_at, invoice_issue.created_at) AS updated_at,
    invoice_issue.deleted_flag
FROM fm_invoice_issue invoice_issue
LEFT JOIN invoice_issue_sources
  ON invoice_issue_sources.record_id = invoice_issue.id
UNION ALL
SELECT
    'ledger-adjustment'::text AS module_key,
    ledger_adjustment.id AS record_id,
    ledger_adjustment.adjustment_no::text AS primary_no,
    concat_ws(' / ',
        NULLIF(BTRIM(ledger_adjustment.counterparty_name), ''),
        NULLIF(BTRIM(ledger_adjustment.project_name), ''),
        NULLIF(BTRIM(ledger_adjustment.status), '')
    ) AS summary,
    concat_ws(' ',
        ledger_adjustment.id::text,
        ledger_adjustment.adjustment_no,
        ledger_adjustment.direction,
        ledger_adjustment.counterparty_type,
        ledger_adjustment.counterparty_code,
        ledger_adjustment.counterparty_name,
        ledger_adjustment.project_id::text,
        ledger_adjustment.project_name,
        ledger_adjustment.adjustment_type,
        ledger_adjustment.effect,
        ledger_adjustment.operator_name,
        ledger_adjustment.status,
        ledger_adjustment.remark
    ) AS search_text,
    ledger_adjustment.created_by,
    COALESCE(ledger_adjustment.updated_at, ledger_adjustment.created_at) AS updated_at,
    ledger_adjustment.deleted_flag
FROM fm_ledger_adjustment ledger_adjustment;
