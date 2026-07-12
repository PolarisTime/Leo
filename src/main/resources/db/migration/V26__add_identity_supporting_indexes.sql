CREATE INDEX idx_md_project_customer_id ON public.md_project (customer_id);
CREATE INDEX idx_so_sales_order_customer_id ON public.so_sales_order (customer_id);

CREATE INDEX idx_ct_purchase_contract_supplier_id ON public.ct_purchase_contract (supplier_id);
CREATE INDEX idx_ct_purchase_contract_item_material_id ON public.ct_purchase_contract_item (material_id);
CREATE INDEX idx_ct_sales_contract_customer_id ON public.ct_sales_contract (customer_id);
CREATE INDEX idx_ct_sales_contract_project_id ON public.ct_sales_contract (project_id);
CREATE INDEX idx_ct_sales_contract_item_material_id ON public.ct_sales_contract_item (material_id);

CREATE INDEX idx_po_purchase_order_supplier_id ON public.po_purchase_order (supplier_id);
CREATE INDEX idx_po_purchase_order_item_material_id ON public.po_purchase_order_item (material_id);
CREATE INDEX idx_po_purchase_order_item_warehouse_id ON public.po_purchase_order_item (warehouse_id);
CREATE INDEX idx_po_purchase_order_item_stock_identity ON public.po_purchase_order_item (material_id, warehouse_id, batch_no_normalized);
CREATE INDEX idx_po_purchase_inbound_supplier_id ON public.po_purchase_inbound (supplier_id);
CREATE INDEX idx_po_purchase_inbound_warehouse_id ON public.po_purchase_inbound (warehouse_id);
CREATE INDEX idx_po_purchase_inbound_item_material_id ON public.po_purchase_inbound_item (material_id);
CREATE INDEX idx_po_purchase_inbound_item_warehouse_id ON public.po_purchase_inbound_item (warehouse_id);
CREATE INDEX idx_po_purchase_inbound_item_stock_identity ON public.po_purchase_inbound_item (material_id, warehouse_id, batch_no_normalized);
CREATE INDEX idx_po_purchase_refund_supplier_id ON public.po_purchase_refund (supplier_id);
CREATE INDEX idx_po_purchase_refund_item_material_id ON public.po_purchase_refund_item (material_id);
CREATE INDEX idx_po_purchase_refund_item_warehouse_id ON public.po_purchase_refund_item (warehouse_id);

CREATE INDEX idx_so_sales_order_item_material_id ON public.so_sales_order_item (material_id);
CREATE INDEX idx_so_sales_order_item_warehouse_id ON public.so_sales_order_item (warehouse_id);
CREATE INDEX idx_so_sales_order_item_stock_identity ON public.so_sales_order_item (material_id, warehouse_id, batch_no_normalized);
CREATE INDEX idx_so_sales_outbound_customer_id ON public.so_sales_outbound (customer_id);
CREATE INDEX idx_so_sales_outbound_project_id ON public.so_sales_outbound (project_id);
CREATE INDEX idx_so_sales_outbound_warehouse_id ON public.so_sales_outbound (warehouse_id);
CREATE INDEX idx_so_sales_outbound_item_material_id ON public.so_sales_outbound_item (material_id);
CREATE INDEX idx_so_sales_outbound_item_warehouse_id ON public.so_sales_outbound_item (warehouse_id);
CREATE INDEX idx_so_sales_outbound_item_stock_identity ON public.so_sales_outbound_item (material_id, warehouse_id, batch_no_normalized);

CREATE INDEX idx_lg_freight_bill_carrier_id ON public.lg_freight_bill (carrier_id);
CREATE INDEX idx_lg_freight_bill_vehicle_id ON public.lg_freight_bill (vehicle_id);
CREATE INDEX idx_lg_freight_bill_item_customer_id ON public.lg_freight_bill_item (customer_id);
CREATE INDEX idx_lg_freight_bill_item_project_id ON public.lg_freight_bill_item (project_id);
CREATE INDEX idx_lg_freight_bill_item_material_id ON public.lg_freight_bill_item (material_id);
CREATE INDEX idx_lg_freight_bill_item_warehouse_id ON public.lg_freight_bill_item (warehouse_id);
CREATE INDEX idx_st_freight_statement_carrier_id ON public.st_freight_statement (carrier_id);
CREATE INDEX idx_st_freight_statement_item_bill_id ON public.st_freight_statement_item (source_freight_bill_id);
CREATE INDEX idx_st_freight_statement_item_bill_item_id ON public.st_freight_statement_item (source_freight_bill_item_id);
CREATE INDEX idx_st_freight_statement_item_material_id ON public.st_freight_statement_item (material_id);
CREATE INDEX idx_st_freight_statement_item_warehouse_id ON public.st_freight_statement_item (warehouse_id);

CREATE INDEX idx_fm_invoice_issue_customer_id ON public.fm_invoice_issue (customer_id);
CREATE INDEX idx_fm_invoice_issue_project_id ON public.fm_invoice_issue (project_id);
CREATE INDEX idx_fm_invoice_issue_item_material_id ON public.fm_invoice_issue_item (material_id);
CREATE INDEX idx_fm_invoice_issue_item_warehouse_id ON public.fm_invoice_issue_item (warehouse_id);
CREATE INDEX idx_fm_invoice_receipt_supplier_id ON public.fm_invoice_receipt (supplier_id);
CREATE INDEX idx_fm_invoice_receipt_item_material_id ON public.fm_invoice_receipt_item (material_id);
CREATE INDEX idx_fm_invoice_receipt_item_warehouse_id ON public.fm_invoice_receipt_item (warehouse_id);
CREATE INDEX idx_fm_receipt_customer_id ON public.fm_receipt (customer_id);
CREATE INDEX idx_fm_receipt_allocation_customer_statement_id ON public.fm_receipt_allocation (source_customer_statement_id);
CREATE INDEX idx_fm_payment_counterparty_id ON public.fm_payment (counterparty_type, counterparty_id);
CREATE INDEX idx_fm_payment_alloc_supplier_statement_id ON public.fm_payment_allocation (source_supplier_statement_id);
CREATE INDEX idx_fm_payment_alloc_freight_statement_id ON public.fm_payment_allocation (source_freight_statement_id);
CREATE INDEX idx_fm_supplier_refund_supplier_id ON public.fm_supplier_refund_receipt (supplier_id);
CREATE INDEX idx_fm_ledger_adjustment_counterparty_id ON public.fm_ledger_adjustment (counterparty_type, counterparty_id);

CREATE INDEX idx_st_customer_statement_customer_id ON public.st_customer_statement (customer_id);
CREATE INDEX idx_st_customer_statement_item_customer_id ON public.st_customer_statement_item (customer_id);
CREATE INDEX idx_st_customer_statement_item_material_id ON public.st_customer_statement_item (material_id);
CREATE INDEX idx_st_customer_statement_item_warehouse_id ON public.st_customer_statement_item (warehouse_id);
CREATE INDEX idx_st_supplier_statement_supplier_id ON public.st_supplier_statement (supplier_id);
CREATE INDEX idx_st_supplier_statement_item_material_id ON public.st_supplier_statement_item (material_id);
CREATE INDEX idx_st_supplier_statement_item_warehouse_id ON public.st_supplier_statement_item (warehouse_id);
