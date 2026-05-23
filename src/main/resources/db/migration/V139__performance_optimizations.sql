-- V139: Database performance optimizations
-- P0: Partial indexes + Fillfactor + BRIN

-- ============================================================
-- 1. 部分索引（跳过软删除行，索引缩小 30-50%）
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_po_purchase_order_active_status
    ON po_purchase_order (status) WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_so_sales_order_active_status
    ON so_sales_order (status) WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_active_status
    ON lg_freight_bill (status) WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_st_customer_statement_active
    ON st_customer_statement (status) WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_po_purchase_order_active_date
    ON po_purchase_order (order_date) WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_so_sales_order_active_date
    ON so_sales_order (delivery_date) WHERE deleted_flag = FALSE;

-- ============================================================
-- 2. BRIN 索引（操作日志表，体积仅 B-tree 的 1/100）
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_operation_log_brin_time
    ON sys_operation_log USING brin (operation_time) WITH (pages_per_range = 64);

-- ============================================================
-- 3. Fillfactor（高频 UPDATE 表预留 30% 页内空间给 HOT 更新）
-- ============================================================

ALTER TABLE po_purchase_order SET (fillfactor = 70);
ALTER TABLE so_sales_order SET (fillfactor = 70);
ALTER TABLE po_purchase_inbound SET (fillfactor = 70);
ALTER TABLE so_sales_outbound SET (fillfactor = 70);
ALTER TABLE lg_freight_bill SET (fillfactor = 70);
ALTER TABLE st_customer_statement SET (fillfactor = 70);
ALTER TABLE st_supplier_statement SET (fillfactor = 70);
ALTER TABLE st_freight_statement SET (fillfactor = 70);
ALTER TABLE fm_payment SET (fillfactor = 70);
ALTER TABLE fm_receipt SET (fillfactor = 70);
ALTER TABLE fm_invoice_issue SET (fillfactor = 70);
ALTER TABLE fm_invoice_receipt SET (fillfactor = 70);
ALTER TABLE ct_sales_contract SET (fillfactor = 70);
ALTER TABLE ct_purchase_contract SET (fillfactor = 70);
ALTER TABLE sys_user SET (fillfactor = 80);

-- ============================================================
-- 4. 覆盖索引（列表查询避免回表）
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_po_order_list
    ON po_purchase_order (status, order_date DESC, id DESC)
    INCLUDE (supplier_name, total_weight, total_amount, buyer_name)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_so_order_list
    ON so_sales_order (status, delivery_date DESC, id DESC)
    INCLUDE (customer_name, project_name, total_weight, total_amount, sales_name)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_list
    ON lg_freight_bill (status, bill_time DESC, id DESC)
    INCLUDE (carrier_name, customer_name, project_name, total_weight, total_freight)
    WHERE deleted_flag = FALSE;

-- ============================================================
-- 5. Autovacuum 调优（高频更新表更激进地回收死元组）
-- ============================================================

ALTER TABLE po_purchase_order SET (autovacuum_vacuum_scale_factor = 0.02);
ALTER TABLE so_sales_order SET (autovacuum_vacuum_scale_factor = 0.02);
ALTER TABLE po_purchase_inbound SET (autovacuum_vacuum_scale_factor = 0.02);
ALTER TABLE so_sales_outbound SET (autovacuum_vacuum_scale_factor = 0.02);
