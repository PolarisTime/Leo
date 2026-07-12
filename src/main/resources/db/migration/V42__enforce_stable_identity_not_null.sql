ALTER TABLE public.md_project
    ALTER COLUMN customer_id SET NOT NULL,
    DROP CONSTRAINT chk_md_project_customer_identity_nn;

ALTER TABLE public.ct_purchase_contract
    ALTER COLUMN supplier_id SET NOT NULL,
    DROP CONSTRAINT chk_ct_purchase_contract_supplier_identity_nn;
ALTER TABLE public.po_purchase_order
    ALTER COLUMN supplier_id SET NOT NULL,
    DROP CONSTRAINT chk_po_purchase_order_supplier_identity_nn;
ALTER TABLE public.po_purchase_inbound
    ALTER COLUMN supplier_id SET NOT NULL,
    ALTER COLUMN warehouse_id SET NOT NULL,
    DROP CONSTRAINT chk_po_purchase_inbound_identity_nn;
ALTER TABLE public.po_purchase_refund
    ALTER COLUMN supplier_id SET NOT NULL,
    DROP CONSTRAINT chk_po_purchase_refund_supplier_identity_nn;
ALTER TABLE public.fm_invoice_receipt
    ALTER COLUMN supplier_id SET NOT NULL,
    DROP CONSTRAINT chk_fm_invoice_receipt_supplier_identity_nn;
ALTER TABLE public.st_supplier_statement
    ALTER COLUMN supplier_id SET NOT NULL,
    DROP CONSTRAINT chk_st_supplier_statement_supplier_identity_nn;
ALTER TABLE public.fm_supplier_refund_receipt
    ALTER COLUMN supplier_id SET NOT NULL,
    DROP CONSTRAINT chk_fm_supplier_refund_supplier_identity_nn;

ALTER TABLE public.so_sales_order
    ALTER COLUMN customer_id SET NOT NULL,
    ALTER COLUMN project_id SET NOT NULL,
    DROP CONSTRAINT chk_so_sales_order_party_identity_nn;
ALTER TABLE public.ct_sales_contract
    ALTER COLUMN customer_id SET NOT NULL,
    ALTER COLUMN project_id SET NOT NULL,
    DROP CONSTRAINT chk_ct_sales_contract_party_identity_nn;
ALTER TABLE public.so_sales_outbound
    ALTER COLUMN customer_id SET NOT NULL,
    ALTER COLUMN project_id SET NOT NULL,
    ALTER COLUMN warehouse_id SET NOT NULL,
    DROP CONSTRAINT chk_so_sales_outbound_identity_nn;
ALTER TABLE public.fm_invoice_issue
    ALTER COLUMN customer_id SET NOT NULL,
    ALTER COLUMN project_id SET NOT NULL,
    DROP CONSTRAINT chk_fm_invoice_issue_party_identity_nn;
ALTER TABLE public.st_customer_statement
    ALTER COLUMN customer_id SET NOT NULL,
    ALTER COLUMN project_id SET NOT NULL,
    DROP CONSTRAINT chk_st_customer_statement_party_identity_nn;
ALTER TABLE public.st_customer_statement_item
    ALTER COLUMN customer_id SET NOT NULL,
    ALTER COLUMN project_id SET NOT NULL,
    ALTER COLUMN material_id SET NOT NULL,
    DROP CONSTRAINT chk_st_customer_stmt_item_party_identity_nn,
    DROP CONSTRAINT chk_st_customer_stmt_item_material_identity_nn;
ALTER TABLE public.fm_receipt
    ALTER COLUMN customer_id SET NOT NULL,
    ALTER COLUMN project_id SET NOT NULL,
    DROP CONSTRAINT chk_fm_receipt_party_identity_nn;
ALTER TABLE public.lg_freight_bill_item
    ALTER COLUMN customer_id SET NOT NULL,
    ALTER COLUMN project_id SET NOT NULL,
    ALTER COLUMN material_id SET NOT NULL,
    ALTER COLUMN warehouse_id SET NOT NULL,
    ALTER COLUMN source_sales_outbound_item_id SET NOT NULL,
    DROP CONSTRAINT chk_lg_freight_bill_item_party_identity_nn,
    DROP CONSTRAINT chk_lg_freight_bill_item_stock_source_identity_nn;
ALTER TABLE public.st_freight_statement_item
    ALTER COLUMN customer_id SET NOT NULL,
    ALTER COLUMN project_id SET NOT NULL,
    ALTER COLUMN material_id SET NOT NULL,
    ALTER COLUMN source_freight_bill_id SET NOT NULL,
    ALTER COLUMN source_freight_bill_item_id SET NOT NULL,
    DROP CONSTRAINT chk_st_freight_statement_item_party_identity_nn,
    DROP CONSTRAINT chk_st_freight_stmt_item_material_source_identity_nn;

ALTER TABLE public.ct_purchase_contract_item
    ALTER COLUMN material_id SET NOT NULL,
    DROP CONSTRAINT chk_ct_purchase_contract_item_material_identity_nn;
ALTER TABLE public.ct_sales_contract_item
    ALTER COLUMN material_id SET NOT NULL,
    DROP CONSTRAINT chk_ct_sales_contract_item_material_identity_nn;
ALTER TABLE public.po_purchase_order_item
    ALTER COLUMN material_id SET NOT NULL,
    DROP CONSTRAINT chk_po_purchase_order_item_material_identity_nn;
ALTER TABLE public.po_purchase_inbound_item
    ALTER COLUMN material_id SET NOT NULL,
    ALTER COLUMN warehouse_id SET NOT NULL,
    DROP CONSTRAINT chk_po_purchase_inbound_item_stock_identity_nn;
ALTER TABLE public.po_purchase_refund_item
    ALTER COLUMN material_id SET NOT NULL,
    DROP CONSTRAINT chk_po_purchase_refund_item_material_identity_nn;
ALTER TABLE public.so_sales_order_item
    ALTER COLUMN material_id SET NOT NULL,
    DROP CONSTRAINT chk_so_sales_order_item_material_identity_nn;
ALTER TABLE public.so_sales_outbound_item
    ALTER COLUMN material_id SET NOT NULL,
    ALTER COLUMN warehouse_id SET NOT NULL,
    ALTER COLUMN source_sales_order_item_id SET NOT NULL,
    DROP CONSTRAINT chk_so_sales_outbound_item_stock_identity_nn;
ALTER TABLE public.fm_invoice_issue_item
    ALTER COLUMN material_id SET NOT NULL,
    DROP CONSTRAINT chk_fm_invoice_issue_item_material_identity_nn;
ALTER TABLE public.fm_invoice_receipt_item
    ALTER COLUMN material_id SET NOT NULL,
    DROP CONSTRAINT chk_fm_invoice_receipt_item_material_identity_nn;
ALTER TABLE public.st_supplier_statement_item
    ALTER COLUMN material_id SET NOT NULL,
    DROP CONSTRAINT chk_st_supplier_stmt_item_material_identity_nn;

ALTER TABLE public.lg_freight_bill
    ALTER COLUMN carrier_id SET NOT NULL,
    DROP CONSTRAINT chk_lg_freight_bill_carrier_identity_nn;
ALTER TABLE public.st_freight_statement
    ALTER COLUMN carrier_id SET NOT NULL,
    DROP CONSTRAINT chk_st_freight_statement_carrier_identity_nn;

ALTER TABLE public.fm_payment
    ALTER COLUMN counterparty_type SET NOT NULL,
    ALTER COLUMN counterparty_id SET NOT NULL;
ALTER TABLE public.fm_ledger_adjustment
    ALTER COLUMN counterparty_id SET NOT NULL,
    DROP CONSTRAINT chk_fm_ledger_adjustment_counterparty_identity_nn;
ALTER TABLE public.fm_receipt_allocation
    ALTER COLUMN source_customer_statement_id SET NOT NULL;
