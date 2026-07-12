-- Parent composite keys used by ownership/source-pair foreign keys.
ALTER TABLE public.md_project
    ADD CONSTRAINT uk_md_project_id_customer_identity
        UNIQUE USING INDEX uk_md_project_id_customer_identity,
    ADD CONSTRAINT fk_md_project_customer_identity
        FOREIGN KEY (customer_id) REFERENCES public.md_customer (id)
        ON DELETE RESTRICT NOT VALID;

ALTER TABLE public.lg_freight_bill_item
    ADD CONSTRAINT uk_lg_freight_bill_item_id_bill_identity
        UNIQUE USING INDEX uk_lg_freight_bill_item_id_bill_identity;

-- Purchase party identity.
ALTER TABLE public.ct_purchase_contract
    ADD CONSTRAINT fk_ct_purchase_contract_supplier_identity
        FOREIGN KEY (supplier_id) REFERENCES public.md_supplier (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.po_purchase_order
    ADD CONSTRAINT fk_po_purchase_order_supplier_identity
        FOREIGN KEY (supplier_id) REFERENCES public.md_supplier (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.po_purchase_inbound
    ADD CONSTRAINT fk_po_purchase_inbound_supplier_identity
        FOREIGN KEY (supplier_id) REFERENCES public.md_supplier (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.po_purchase_refund
    ADD CONSTRAINT fk_po_purchase_refund_supplier_identity
        FOREIGN KEY (supplier_id) REFERENCES public.md_supplier (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_invoice_receipt
    ADD CONSTRAINT fk_fm_invoice_receipt_supplier_identity
        FOREIGN KEY (supplier_id) REFERENCES public.md_supplier (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_supplier_statement
    ADD CONSTRAINT fk_st_supplier_statement_supplier_identity
        FOREIGN KEY (supplier_id) REFERENCES public.md_supplier (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_supplier_refund_receipt
    ADD CONSTRAINT fk_fm_supplier_refund_receipt_supplier_identity
        FOREIGN KEY (supplier_id) REFERENCES public.md_supplier (id)
        ON DELETE RESTRICT NOT VALID;

-- Customer/project identity. Composite foreign keys prevent cross-customer projects.
ALTER TABLE public.so_sales_order
    DROP CONSTRAINT fk_so_sales_order_project,
    ADD CONSTRAINT fk_so_sales_order_customer_identity
        FOREIGN KEY (customer_id) REFERENCES public.md_customer (id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_so_sales_order_customer_project
        FOREIGN KEY (project_id, customer_id)
        REFERENCES public.md_project (id, customer_id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.ct_sales_contract
    ADD CONSTRAINT fk_ct_sales_contract_customer_identity
        FOREIGN KEY (customer_id) REFERENCES public.md_customer (id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_ct_sales_contract_customer_project
        FOREIGN KEY (project_id, customer_id)
        REFERENCES public.md_project (id, customer_id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.so_sales_outbound
    ADD CONSTRAINT fk_so_sales_outbound_customer_identity
        FOREIGN KEY (customer_id) REFERENCES public.md_customer (id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_so_sales_outbound_customer_project
        FOREIGN KEY (project_id, customer_id)
        REFERENCES public.md_project (id, customer_id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_invoice_issue
    ADD CONSTRAINT fk_fm_invoice_issue_customer_identity
        FOREIGN KEY (customer_id) REFERENCES public.md_customer (id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_fm_invoice_issue_customer_project
        FOREIGN KEY (project_id, customer_id)
        REFERENCES public.md_project (id, customer_id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_customer_statement
    DROP CONSTRAINT fk_st_customer_stmt_project,
    ADD CONSTRAINT fk_st_customer_statement_customer_identity
        FOREIGN KEY (customer_id) REFERENCES public.md_customer (id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_st_customer_statement_customer_project
        FOREIGN KEY (project_id, customer_id)
        REFERENCES public.md_project (id, customer_id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_customer_statement_item
    ADD CONSTRAINT fk_st_customer_stmt_item_customer_identity
        FOREIGN KEY (customer_id) REFERENCES public.md_customer (id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_st_customer_stmt_item_customer_project
        FOREIGN KEY (project_id, customer_id)
        REFERENCES public.md_project (id, customer_id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_receipt
    DROP CONSTRAINT fk_fm_receipt_project,
    ADD CONSTRAINT fk_fm_receipt_customer_identity
        FOREIGN KEY (customer_id) REFERENCES public.md_customer (id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_fm_receipt_customer_project
        FOREIGN KEY (project_id, customer_id)
        REFERENCES public.md_project (id, customer_id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.lg_freight_bill_item
    ADD CONSTRAINT fk_lg_freight_bill_item_customer_identity
        FOREIGN KEY (customer_id) REFERENCES public.md_customer (id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_lg_freight_bill_item_customer_project
        FOREIGN KEY (project_id, customer_id)
        REFERENCES public.md_project (id, customer_id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_freight_statement_item
    ADD CONSTRAINT fk_st_freight_statement_item_customer_identity
        FOREIGN KEY (customer_id) REFERENCES public.md_customer (id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_st_freight_statement_item_customer_project
        FOREIGN KEY (project_id, customer_id)
        REFERENCES public.md_project (id, customer_id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_ledger_adjustment
    ADD CONSTRAINT fk_fm_ledger_adjustment_project_identity
        FOREIGN KEY (project_id) REFERENCES public.md_project (id)
        ON DELETE RESTRICT NOT VALID;

-- Material identity.
ALTER TABLE public.ct_purchase_contract_item
    ADD CONSTRAINT fk_ct_purchase_contract_item_material_identity
        FOREIGN KEY (material_id) REFERENCES public.md_material (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.ct_sales_contract_item
    ADD CONSTRAINT fk_ct_sales_contract_item_material_identity
        FOREIGN KEY (material_id) REFERENCES public.md_material (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.po_purchase_order_item
    ADD CONSTRAINT fk_po_purchase_order_item_material_identity
        FOREIGN KEY (material_id) REFERENCES public.md_material (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.po_purchase_inbound_item
    ADD CONSTRAINT fk_po_purchase_inbound_item_material_identity
        FOREIGN KEY (material_id) REFERENCES public.md_material (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.po_purchase_refund_item
    ADD CONSTRAINT fk_po_purchase_refund_item_material_identity
        FOREIGN KEY (material_id) REFERENCES public.md_material (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.so_sales_order_item
    ADD CONSTRAINT fk_so_sales_order_item_material_identity
        FOREIGN KEY (material_id) REFERENCES public.md_material (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.so_sales_outbound_item
    ADD CONSTRAINT fk_so_sales_outbound_item_material_identity
        FOREIGN KEY (material_id) REFERENCES public.md_material (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.lg_freight_bill_item
    ADD CONSTRAINT fk_lg_freight_bill_item_material_identity
        FOREIGN KEY (material_id) REFERENCES public.md_material (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_invoice_issue_item
    ADD CONSTRAINT fk_fm_invoice_issue_item_material_identity
        FOREIGN KEY (material_id) REFERENCES public.md_material (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_invoice_receipt_item
    ADD CONSTRAINT fk_fm_invoice_receipt_item_material_identity
        FOREIGN KEY (material_id) REFERENCES public.md_material (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_customer_statement_item
    ADD CONSTRAINT fk_st_customer_stmt_item_material_identity
        FOREIGN KEY (material_id) REFERENCES public.md_material (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_supplier_statement_item
    ADD CONSTRAINT fk_st_supplier_stmt_item_material_identity
        FOREIGN KEY (material_id) REFERENCES public.md_material (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_freight_statement_item
    ADD CONSTRAINT fk_st_freight_stmt_item_material_identity
        FOREIGN KEY (material_id) REFERENCES public.md_material (id)
        ON DELETE RESTRICT NOT VALID;

-- Warehouse identity. Optional planning/trace rows remain nullable; actual stock facts
-- are made non-null only after check validation in later migrations.
ALTER TABLE public.po_purchase_order_item
    ADD CONSTRAINT fk_po_purchase_order_item_warehouse_identity
        FOREIGN KEY (warehouse_id) REFERENCES public.md_warehouse (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.po_purchase_inbound
    ADD CONSTRAINT fk_po_purchase_inbound_warehouse_identity
        FOREIGN KEY (warehouse_id) REFERENCES public.md_warehouse (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.po_purchase_inbound_item
    ADD CONSTRAINT fk_po_purchase_inbound_item_warehouse_identity
        FOREIGN KEY (warehouse_id) REFERENCES public.md_warehouse (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.po_purchase_refund_item
    ADD CONSTRAINT fk_po_purchase_refund_item_warehouse_identity
        FOREIGN KEY (warehouse_id) REFERENCES public.md_warehouse (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.so_sales_order_item
    ADD CONSTRAINT fk_so_sales_order_item_warehouse_identity
        FOREIGN KEY (warehouse_id) REFERENCES public.md_warehouse (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.so_sales_outbound
    ADD CONSTRAINT fk_so_sales_outbound_warehouse_identity
        FOREIGN KEY (warehouse_id) REFERENCES public.md_warehouse (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.so_sales_outbound_item
    ADD CONSTRAINT fk_so_sales_outbound_item_warehouse_identity
        FOREIGN KEY (warehouse_id) REFERENCES public.md_warehouse (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.lg_freight_bill_item
    ADD CONSTRAINT fk_lg_freight_bill_item_warehouse_identity
        FOREIGN KEY (warehouse_id) REFERENCES public.md_warehouse (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_invoice_issue_item
    ADD CONSTRAINT fk_fm_invoice_issue_item_warehouse_identity
        FOREIGN KEY (warehouse_id) REFERENCES public.md_warehouse (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_invoice_receipt_item
    ADD CONSTRAINT fk_fm_invoice_receipt_item_warehouse_identity
        FOREIGN KEY (warehouse_id) REFERENCES public.md_warehouse (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_customer_statement_item
    ADD CONSTRAINT fk_st_customer_stmt_item_warehouse_identity
        FOREIGN KEY (warehouse_id) REFERENCES public.md_warehouse (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_supplier_statement_item
    ADD CONSTRAINT fk_st_supplier_stmt_item_warehouse_identity
        FOREIGN KEY (warehouse_id) REFERENCES public.md_warehouse (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_freight_statement_item
    ADD CONSTRAINT fk_st_freight_stmt_item_warehouse_identity
        FOREIGN KEY (warehouse_id) REFERENCES public.md_warehouse (id)
        ON DELETE RESTRICT NOT VALID;

-- Logistics master and direct-source identity.
ALTER TABLE public.lg_freight_bill
    ADD CONSTRAINT fk_lg_freight_bill_carrier_identity
        FOREIGN KEY (carrier_id) REFERENCES public.md_carrier (id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_lg_freight_bill_vehicle_identity
        FOREIGN KEY (vehicle_id) REFERENCES public.md_vehicle (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_freight_statement
    ADD CONSTRAINT fk_st_freight_statement_carrier_identity
        FOREIGN KEY (carrier_id) REFERENCES public.md_carrier (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_freight_statement_item
    ADD CONSTRAINT fk_st_freight_statement_item_source_bill
        FOREIGN KEY (source_freight_bill_id) REFERENCES public.lg_freight_bill (id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_st_freight_statement_item_source_bill_item
        FOREIGN KEY (source_freight_bill_item_id) REFERENCES public.lg_freight_bill_item (id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_st_freight_statement_item_source_bill_pair
        FOREIGN KEY (source_freight_bill_item_id, source_freight_bill_id)
        REFERENCES public.lg_freight_bill_item (id, bill_id)
        ON DELETE RESTRICT NOT VALID;

-- Business source identity that previously had only indexes/service checks.
ALTER TABLE public.po_purchase_inbound_item
    ADD CONSTRAINT fk_po_purchase_inbound_item_source_identity
        FOREIGN KEY (source_purchase_order_item_id)
        REFERENCES public.po_purchase_order_item (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.so_sales_order_item
    ADD CONSTRAINT fk_so_sales_order_item_source_inbound_identity
        FOREIGN KEY (source_inbound_item_id) REFERENCES public.po_purchase_inbound_item (id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_so_sales_order_item_source_purchase_identity
        FOREIGN KEY (source_purchase_order_item_id) REFERENCES public.po_purchase_order_item (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.so_sales_outbound_item
    ADD CONSTRAINT fk_so_sales_outbound_item_source_identity
        FOREIGN KEY (source_sales_order_item_id) REFERENCES public.so_sales_order_item (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.lg_freight_bill_item
    ADD CONSTRAINT fk_lg_freight_bill_item_source_identity
        FOREIGN KEY (source_sales_outbound_item_id) REFERENCES public.so_sales_outbound_item (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_invoice_issue_item
    ADD CONSTRAINT fk_fm_invoice_issue_item_source_identity
        FOREIGN KEY (source_sales_order_item_id) REFERENCES public.so_sales_order_item (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_invoice_receipt_item
    ADD CONSTRAINT fk_fm_invoice_receipt_item_source_identity
        FOREIGN KEY (source_purchase_order_item_id) REFERENCES public.po_purchase_order_item (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_customer_statement_item
    ADD CONSTRAINT fk_st_customer_stmt_item_source_identity
        FOREIGN KEY (source_sales_order_item_id) REFERENCES public.so_sales_order_item (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_supplier_statement_item
    ADD CONSTRAINT fk_st_supplier_stmt_item_source_identity
        FOREIGN KEY (source_inbound_item_id) REFERENCES public.po_purchase_inbound_item (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_freight_statement_item
    ADD CONSTRAINT fk_st_freight_stmt_item_source_outbound_identity
        FOREIGN KEY (source_sales_outbound_item_id) REFERENCES public.so_sales_outbound_item (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_receipt
    ADD CONSTRAINT fk_fm_receipt_source_customer_statement
        FOREIGN KEY (source_customer_statement_id) REFERENCES public.st_customer_statement (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_receipt_allocation
    ADD CONSTRAINT fk_fm_receipt_allocation_customer_statement
        FOREIGN KEY (source_customer_statement_id) REFERENCES public.st_customer_statement (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_payment_allocation
    ADD CONSTRAINT fk_fm_payment_allocation_supplier_statement
        FOREIGN KEY (source_supplier_statement_id) REFERENCES public.st_supplier_statement (id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT fk_fm_payment_allocation_freight_statement
        FOREIGN KEY (source_freight_statement_id) REFERENCES public.st_freight_statement (id)
        ON DELETE RESTRICT NOT VALID;

-- Settlement-company identity, including soft-deleted historical references.
ALTER TABLE public.md_carrier
    ADD CONSTRAINT fk_md_carrier_default_settlement_company
        FOREIGN KEY (default_settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.md_customer
    ADD CONSTRAINT fk_md_customer_default_settlement_company
        FOREIGN KEY (default_settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.po_purchase_order
    ADD CONSTRAINT fk_po_purchase_order_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.po_purchase_inbound
    ADD CONSTRAINT fk_po_purchase_inbound_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.po_purchase_inbound_item
    ADD CONSTRAINT fk_po_purchase_inbound_item_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.po_purchase_refund
    ADD CONSTRAINT fk_po_purchase_refund_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.so_sales_order
    ADD CONSTRAINT fk_so_sales_order_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.so_sales_order_item
    ADD CONSTRAINT fk_so_sales_order_item_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.so_sales_outbound
    ADD CONSTRAINT fk_so_sales_outbound_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.so_sales_outbound_item
    ADD CONSTRAINT fk_so_sales_outbound_item_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.lg_freight_bill
    ADD CONSTRAINT fk_lg_freight_bill_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.lg_freight_bill_item
    ADD CONSTRAINT fk_lg_freight_bill_item_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_customer_statement
    ADD CONSTRAINT fk_st_customer_statement_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_supplier_statement
    ADD CONSTRAINT fk_st_supplier_statement_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_freight_statement
    ADD CONSTRAINT fk_st_freight_statement_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.st_freight_statement_item
    ADD CONSTRAINT fk_st_freight_statement_item_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_invoice_issue
    ADD CONSTRAINT fk_fm_invoice_issue_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_invoice_receipt
    ADD CONSTRAINT fk_fm_invoice_receipt_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_receipt
    ADD CONSTRAINT fk_fm_receipt_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_payment
    ADD CONSTRAINT fk_fm_payment_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_supplier_refund_receipt
    ADD CONSTRAINT fk_fm_supplier_refund_receipt_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.fm_ledger_adjustment
    ADD CONSTRAINT fk_fm_ledger_adjustment_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
ALTER TABLE public.sys_print_template
    ADD CONSTRAINT fk_sys_print_template_settlement_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT NOT VALID;
