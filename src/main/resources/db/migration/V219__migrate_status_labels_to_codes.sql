-- V219: 把状态列的中文展示值迁移为稳定英文 code。
--
-- 背景：审计要求 API 枚举值稳定。此前 status 列直接存中文 label（正常/草稿/已审核/完成采购…），
-- 本次迁移后数据库存英文 code；后端 StatusConstants/DocumentStatus、前端词表与 SQL 字面量
-- 必须同步改用 code。已付款/已收款为历史财务状态，报价为报价单状态，成功/失败为操作日志结果。
--
-- 兼容性：本迁移只把「精确匹配中文 label」的值改成 code，已是英文 code 的值原样保留
-- （例如 sys_user.status=NORMAL/DISABLED、sys_print_template.status=ACTIVE）。
-- 回滚映射见 docs/status-code-migration.md；破坏性数据变更按「应用先兼容双读、再迁移数据」执行。
--
-- 迁移分五步（顺序不可调换）：
--   1. 状态列扩容到 varchar(32)：最长 code 为 DELIVERY_VERIFICATION(20)，原 varchar(16)
--      装不下，不先扩容 UPDATE 会直接报 value too long；Java 侧 @Column/@Size 同步改为 32。
--      扩容前需暂存并删除依赖这些列的 global_search_document 视图（PG 不允许带视图依赖改列类型）。
--   2. 删除仅允许中文 label 的 CHECK 约束（否则第 3 步 UPDATE 会违反约束）。
--   3. 修正列默认值（md_*/sys_role 的 '正常'、mk_quote_sheet 的 '报价'）并迁移数据。
--   4. 用 code 取值重建 CHECK 约束，保持原有的取值校验强度。
--   5. 按迁移前定义重建 global_search_document 视图。
--
-- 标签映射除用户确认的 25 条外，另补齐 3 个只存在于历史库/约束中的取值：
--   已归档 → ARCHIVED（ct_* 合同的归档态，用户映射里只列了「归档」）
--   已开票 → INVOICE_ISSUED（fm_invoice_issue 的终态）
--   已收票 → INVOICE_RECEIVED（fm_invoice_receipt 的终态）

-- ---------------------------------------------------------------------------
-- 1. 状态列扩容：varchar(16) → varchar(32)
-- ---------------------------------------------------------------------------
-- global_search_document（V208 重建的全局搜索视图）直接引用这些 status 列，
-- PostgreSQL 不允许在视图依赖期间 ALTER COLUMN TYPE。这里先暂存视图定义并删除视图，
-- 扩容与数据迁移完成后按原定义重建（视图仅把 status 转成 text 展示，不改变列结构）。
CREATE TEMPORARY TABLE status_dependent_view_defs (
    view_name  TEXT PRIMARY KEY,
    definition TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO status_dependent_view_defs (view_name, definition)
SELECT 'global_search_document', pg_get_viewdef('public.global_search_document'::regclass, true);

DROP VIEW public.global_search_document;

-- sys_operation_log 为分区父表，只改父表；PostgreSQL 会把列类型变更传播到全部分区。
ALTER TABLE ct_purchase_contract ALTER COLUMN status TYPE varchar(32);
ALTER TABLE ct_sales_contract ALTER COLUMN status TYPE varchar(32);
ALTER TABLE fm_cash_reversal ALTER COLUMN status TYPE varchar(32);
ALTER TABLE fm_invoice_issue ALTER COLUMN status TYPE varchar(32);
ALTER TABLE fm_invoice_receipt ALTER COLUMN status TYPE varchar(32);
ALTER TABLE fm_ledger_adjustment ALTER COLUMN status TYPE varchar(32);
ALTER TABLE fm_payment ALTER COLUMN status TYPE varchar(32);
ALTER TABLE fm_receipt ALTER COLUMN status TYPE varchar(32);
ALTER TABLE fm_supplier_refund_receipt ALTER COLUMN status TYPE varchar(32);
ALTER TABLE lg_freight_bill ALTER COLUMN status TYPE varchar(32);
ALTER TABLE md_carrier ALTER COLUMN status TYPE varchar(32);
ALTER TABLE md_customer ALTER COLUMN status TYPE varchar(32);
ALTER TABLE md_length_premium ALTER COLUMN status TYPE varchar(32);
ALTER TABLE md_material ALTER COLUMN status TYPE varchar(32);
ALTER TABLE md_material_category ALTER COLUMN status TYPE varchar(32);
ALTER TABLE md_project ALTER COLUMN status TYPE varchar(32);
ALTER TABLE md_quote_region ALTER COLUMN status TYPE varchar(32);
ALTER TABLE md_supplier ALTER COLUMN status TYPE varchar(32);
ALTER TABLE md_warehouse ALTER COLUMN status TYPE varchar(32);
ALTER TABLE mk_quote_sheet ALTER COLUMN status TYPE varchar(32);
ALTER TABLE mk_supplier_price_list ALTER COLUMN status TYPE varchar(32);
ALTER TABLE po_purchase_inbound ALTER COLUMN status TYPE varchar(32);
ALTER TABLE po_purchase_order ALTER COLUMN status TYPE varchar(32);
ALTER TABLE po_purchase_refund ALTER COLUMN status TYPE varchar(32);
ALTER TABLE so_sales_contract ALTER COLUMN status TYPE varchar(32);
ALTER TABLE so_sales_order ALTER COLUMN status TYPE varchar(32);
ALTER TABLE so_sales_outbound ALTER COLUMN status TYPE varchar(32);
ALTER TABLE so_sales_return ALTER COLUMN status TYPE varchar(32);
ALTER TABLE st_customer_statement ALTER COLUMN status TYPE varchar(32);
ALTER TABLE st_freight_statement ALTER COLUMN status TYPE varchar(32);
ALTER TABLE st_supplier_statement ALTER COLUMN status TYPE varchar(32);
ALTER TABLE sys_company_setting ALTER COLUMN status TYPE varchar(32);
ALTER TABLE sys_print_template ALTER COLUMN status TYPE varchar(32);
ALTER TABLE sys_role ALTER COLUMN status TYPE varchar(32);
ALTER TABLE sys_security_secret ALTER COLUMN status TYPE varchar(32);
ALTER TABLE sys_user ALTER COLUMN status TYPE varchar(32);
ALTER TABLE sys_operation_log ALTER COLUMN result_status TYPE varchar(32);

-- ---------------------------------------------------------------------------
-- 2. 删除仅允许中文 label 的状态 CHECK 约束（迁移完成后用 code 取值重建）
-- ---------------------------------------------------------------------------
-- 已是英文 code 的约束（mk_supplier_price_item / mk_supplier_price_list /
-- sys_print_template / sys_security_secret / sys_user）保持不变。
ALTER TABLE ct_purchase_contract DROP CONSTRAINT IF EXISTS chk_purchase_contract_status;
ALTER TABLE ct_sales_contract DROP CONSTRAINT IF EXISTS chk_sales_contract_status;
ALTER TABLE fm_cash_reversal DROP CONSTRAINT IF EXISTS chk_fm_cash_reversal_status;
ALTER TABLE fm_invoice_issue DROP CONSTRAINT IF EXISTS chk_invoice_issue_status;
ALTER TABLE fm_invoice_receipt DROP CONSTRAINT IF EXISTS chk_invoice_receipt_status;
ALTER TABLE fm_ledger_adjustment DROP CONSTRAINT IF EXISTS chk_ledger_adjustment_status;
ALTER TABLE fm_payment DROP CONSTRAINT IF EXISTS chk_payment_status;
ALTER TABLE fm_receipt DROP CONSTRAINT IF EXISTS chk_receipt_status;
ALTER TABLE fm_supplier_refund_receipt DROP CONSTRAINT IF EXISTS chk_fm_supplier_refund_receipt_status;
ALTER TABLE lg_freight_bill DROP CONSTRAINT IF EXISTS chk_freight_bill_status;
ALTER TABLE md_carrier DROP CONSTRAINT IF EXISTS chk_carrier_status;
ALTER TABLE md_customer DROP CONSTRAINT IF EXISTS chk_customer_status;
ALTER TABLE md_length_premium DROP CONSTRAINT IF EXISTS ck_length_premium_status;
ALTER TABLE md_material_category DROP CONSTRAINT IF EXISTS chk_material_category_status;
ALTER TABLE md_project DROP CONSTRAINT IF EXISTS chk_project_status;
ALTER TABLE md_quote_region DROP CONSTRAINT IF EXISTS ck_quote_region_status;
ALTER TABLE md_supplier DROP CONSTRAINT IF EXISTS chk_supplier_status;
ALTER TABLE md_warehouse DROP CONSTRAINT IF EXISTS chk_warehouse_status;
ALTER TABLE po_purchase_inbound DROP CONSTRAINT IF EXISTS chk_inbound_status;
ALTER TABLE po_purchase_order DROP CONSTRAINT IF EXISTS chk_po_status;
ALTER TABLE po_purchase_refund DROP CONSTRAINT IF EXISTS chk_po_purchase_refund_status;
ALTER TABLE so_sales_order DROP CONSTRAINT IF EXISTS chk_so_status;
ALTER TABLE so_sales_outbound DROP CONSTRAINT IF EXISTS chk_outbound_status;
ALTER TABLE so_sales_return DROP CONSTRAINT IF EXISTS chk_so_sales_return_status;
ALTER TABLE st_customer_statement DROP CONSTRAINT IF EXISTS chk_customer_stmt_status;
ALTER TABLE st_freight_statement DROP CONSTRAINT IF EXISTS chk_freight_stmt_status;
ALTER TABLE st_supplier_statement DROP CONSTRAINT IF EXISTS chk_supplier_stmt_status;
ALTER TABLE sys_company_setting DROP CONSTRAINT IF EXISTS chk_company_status;
ALTER TABLE sys_role DROP CONSTRAINT IF EXISTS chk_sys_role_status;

-- ---------------------------------------------------------------------------
-- 3. 修正列默认值（仍为中文 label 的默认值）并迁移数据
-- ---------------------------------------------------------------------------
ALTER TABLE md_length_premium ALTER COLUMN status SET DEFAULT 'NORMAL';
ALTER TABLE md_material ALTER COLUMN status SET DEFAULT 'NORMAL';
ALTER TABLE md_material_category ALTER COLUMN status SET DEFAULT 'NORMAL';
ALTER TABLE md_project ALTER COLUMN status SET DEFAULT 'NORMAL';
ALTER TABLE md_quote_region ALTER COLUMN status SET DEFAULT 'NORMAL';
ALTER TABLE sys_role ALTER COLUMN status SET DEFAULT 'NORMAL';
ALTER TABLE mk_quote_sheet ALTER COLUMN status SET DEFAULT 'QUOTING';

CREATE TEMPORARY TABLE status_label_map (
    label VARCHAR(64) PRIMARY KEY,
    code  VARCHAR(64) NOT NULL
) ON COMMIT DROP;

INSERT INTO status_label_map (label, code) VALUES
    ('正常', 'NORMAL'),
    ('禁用', 'DISABLED'),
    ('草稿', 'DRAFT'),
    ('已审核', 'AUDITED'),
    ('审核', 'CONTRACT_REVIEWED'),
    ('签发', 'CONTRACT_ISSUED'),
    ('已完成', 'COMPLETED'),
    ('作废', 'VOIDED'),
    ('完成采购', 'PURCHASE_COMPLETED'),
    ('完成销售', 'SALES_COMPLETED'),
    ('完成入库', 'INBOUND_COMPLETED'),
    ('交付核定', 'DELIVERY_VERIFICATION'),
    ('已签署', 'SIGNED'),
    ('未签署', 'UNSIGNED'),
    ('未审核', 'UNAUDITED'),
    ('执行中', 'EXECUTING'),
    ('归档', 'ARCHIVED'),
    ('已归档', 'ARCHIVED'),
    ('待确认', 'PENDING_CONFIRM'),
    ('已确认', 'CONFIRMED'),
    ('待审核', 'PENDING_AUDIT'),
    ('报价', 'QUOTING'),
    ('成功', 'SUCCESS'),
    ('失败', 'FAILED'),
    ('已付款', 'PAID'),
    ('已收款', 'RECEIVED'),
    ('已开票', 'INVOICE_ISSUED'),
    ('已收票', 'INVOICE_RECEIVED');

-- 主数据 status
UPDATE md_carrier c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE md_customer c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE md_length_premium c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE md_material c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE md_material_category c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE md_project c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE md_quote_region c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE md_supplier c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE md_warehouse c SET status = m.code FROM status_label_map m WHERE c.status = m.label;

-- 合同/资金
UPDATE ct_purchase_contract c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE ct_sales_contract c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE fm_cash_reversal c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE fm_invoice_issue c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE fm_invoice_receipt c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE fm_ledger_adjustment c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE fm_payment c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE fm_receipt c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE fm_supplier_refund_receipt c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE lg_freight_bill c SET status = m.code FROM status_label_map m WHERE c.status = m.label;

-- 采购/销售
UPDATE po_purchase_inbound c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE po_purchase_order c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE po_purchase_refund c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE so_sales_contract c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE so_sales_order c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE so_sales_outbound c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE so_sales_return c SET status = m.code FROM status_label_map m WHERE c.status = m.label;

-- 对账单
UPDATE st_customer_statement c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE st_freight_statement c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE st_supplier_statement c SET status = m.code FROM status_label_map m WHERE c.status = m.label;

-- 报价单
UPDATE mk_quote_sheet c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE mk_supplier_price_list c SET status = m.code FROM status_label_map m WHERE c.status = m.label;

-- 系统
UPDATE sys_company_setting c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE sys_print_template c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE sys_role c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE sys_security_secret c SET status = m.code FROM status_label_map m WHERE c.status = m.label;
UPDATE sys_user c SET status = m.code FROM status_label_map m WHERE c.status = m.label;

-- 操作日志 result_status（父表是分区表，UPDATE 父表会路由到各分区）
UPDATE sys_operation_log c SET result_status = m.code FROM status_label_map m WHERE c.result_status = m.label;

-- ---------------------------------------------------------------------------
-- 4. 用 code 取值重建 CHECK 约束（与迁移前的中文取值集合一一对应）
-- ---------------------------------------------------------------------------
ALTER TABLE ct_purchase_contract ADD CONSTRAINT chk_purchase_contract_status
    CHECK (status IN ('DRAFT', 'EXECUTING', 'SIGNED', 'ARCHIVED'));
ALTER TABLE ct_sales_contract ADD CONSTRAINT chk_sales_contract_status
    CHECK (status IN ('DRAFT', 'EXECUTING', 'SIGNED', 'ARCHIVED'));
ALTER TABLE fm_cash_reversal ADD CONSTRAINT chk_fm_cash_reversal_status
    CHECK (status IN ('DRAFT', 'AUDITED'));
ALTER TABLE fm_invoice_issue ADD CONSTRAINT chk_invoice_issue_status
    CHECK (status IN ('DRAFT', 'INVOICE_ISSUED'));
ALTER TABLE fm_invoice_receipt ADD CONSTRAINT chk_invoice_receipt_status
    CHECK (status IN ('DRAFT', 'INVOICE_RECEIVED'));
ALTER TABLE fm_ledger_adjustment ADD CONSTRAINT chk_ledger_adjustment_status
    CHECK (status IN ('DRAFT', 'AUDITED'));
ALTER TABLE fm_payment ADD CONSTRAINT chk_payment_status
    CHECK (status IN ('DRAFT', 'AUDITED'));
ALTER TABLE fm_receipt ADD CONSTRAINT chk_receipt_status
    CHECK (status IN ('DRAFT', 'AUDITED'));
ALTER TABLE fm_supplier_refund_receipt ADD CONSTRAINT chk_fm_supplier_refund_receipt_status
    CHECK (status IN ('DRAFT', 'RECEIVED'));
ALTER TABLE lg_freight_bill ADD CONSTRAINT chk_freight_bill_status
    CHECK (status IN ('DRAFT', 'AUDITED'));
ALTER TABLE md_carrier ADD CONSTRAINT chk_carrier_status
    CHECK (status IN ('NORMAL', 'DISABLED'));
ALTER TABLE md_customer ADD CONSTRAINT chk_customer_status
    CHECK (status IN ('NORMAL', 'DISABLED'));
ALTER TABLE md_length_premium ADD CONSTRAINT ck_length_premium_status
    CHECK (status IN ('NORMAL', 'DISABLED'));
ALTER TABLE md_material_category ADD CONSTRAINT chk_material_category_status
    CHECK (status IN ('NORMAL', 'DISABLED'));
ALTER TABLE md_project ADD CONSTRAINT chk_project_status
    CHECK (status IN ('NORMAL', 'DISABLED'));
ALTER TABLE md_quote_region ADD CONSTRAINT ck_quote_region_status
    CHECK (status IN ('NORMAL', 'DISABLED'));
ALTER TABLE md_supplier ADD CONSTRAINT chk_supplier_status
    CHECK (status IN ('NORMAL', 'DISABLED'));
ALTER TABLE md_warehouse ADD CONSTRAINT chk_warehouse_status
    CHECK (status IN ('NORMAL', 'DISABLED'));
ALTER TABLE po_purchase_inbound ADD CONSTRAINT chk_inbound_status
    CHECK (status IN ('DRAFT', 'AUDITED', 'INBOUND_COMPLETED'));
ALTER TABLE po_purchase_order ADD CONSTRAINT chk_po_status
    CHECK (status IN ('DRAFT', 'AUDITED', 'PURCHASE_COMPLETED'));
ALTER TABLE po_purchase_refund ADD CONSTRAINT chk_po_purchase_refund_status
    CHECK (status IN ('DRAFT', 'AUDITED'));
ALTER TABLE so_sales_order ADD CONSTRAINT chk_so_status
    CHECK (status IN ('DRAFT', 'AUDITED', 'DELIVERY_VERIFICATION', 'SALES_COMPLETED'));
ALTER TABLE so_sales_outbound ADD CONSTRAINT chk_outbound_status
    CHECK (status IN ('DRAFT', 'AUDITED'));
ALTER TABLE so_sales_return ADD CONSTRAINT chk_so_sales_return_status
    CHECK (status IN ('DRAFT', 'AUDITED'));
ALTER TABLE st_customer_statement ADD CONSTRAINT chk_customer_stmt_status
    CHECK (status IN ('PENDING_CONFIRM', 'CONFIRMED'));
ALTER TABLE st_freight_statement ADD CONSTRAINT chk_freight_stmt_status
    CHECK (status IN ('DRAFT', 'AUDITED'));
ALTER TABLE st_supplier_statement ADD CONSTRAINT chk_supplier_stmt_status
    CHECK (status IN ('PENDING_CONFIRM', 'CONFIRMED'));
ALTER TABLE sys_company_setting ADD CONSTRAINT chk_company_status
    CHECK (status IN ('NORMAL', 'DISABLED'));
ALTER TABLE sys_role ADD CONSTRAINT chk_sys_role_status
    CHECK (status IN ('NORMAL', 'DISABLED'));

-- ---------------------------------------------------------------------------
-- 5. 按迁移前的定义重建全局搜索视图（列类型不受 status 扩容影响）
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    view_definition TEXT;
BEGIN
    SELECT definition INTO view_definition
    FROM status_dependent_view_defs
    WHERE view_name = 'global_search_document';

    IF view_definition IS NULL THEN
        RAISE EXCEPTION 'global_search_document 视图定义未暂存，无法重建';
    END IF;

    EXECUTE 'CREATE VIEW public.global_search_document AS ' || view_definition;
END $$;
