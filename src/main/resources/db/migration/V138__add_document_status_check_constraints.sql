-- V138: Document workflow status CHECK constraints
-- All status values verified against StatusConstants.ALLOWED_* sets
-- Each validated by BusinessStatusValidator in service layer

-- Purchase Order: 草稿 → 已审核 → 完成采购
ALTER TABLE po_purchase_order DROP CONSTRAINT IF EXISTS chk_po_status;
ALTER TABLE po_purchase_order ADD CONSTRAINT chk_po_status
    CHECK (status IN ('草稿', '已审核', '完成采购')) NOT VALID;

-- Purchase Inbound: 草稿 → 已审核 → 完成入库
ALTER TABLE po_purchase_inbound DROP CONSTRAINT IF EXISTS chk_inbound_status;
ALTER TABLE po_purchase_inbound ADD CONSTRAINT chk_inbound_status
    CHECK (status IN ('草稿', '已审核', '完成入库')) NOT VALID;

-- Sales Order: 草稿 → 已审核 → 待完善 → 完成销售
ALTER TABLE so_sales_order DROP CONSTRAINT IF EXISTS chk_so_status;
ALTER TABLE so_sales_order ADD CONSTRAINT chk_so_status
    CHECK (status IN ('草稿', '已审核', '待完善', '完成销售')) NOT VALID;

-- Sales Outbound: 草稿 → 已审核
ALTER TABLE so_sales_outbound DROP CONSTRAINT IF EXISTS chk_outbound_status;
ALTER TABLE so_sales_outbound ADD CONSTRAINT chk_outbound_status
    CHECK (status IN ('草稿', '已审核')) NOT VALID;

-- Contracts (Sales + Purchase): 草稿 → 执行中 → 已签署 → 已归档
ALTER TABLE ct_sales_contract DROP CONSTRAINT IF EXISTS chk_sales_contract_status;
ALTER TABLE ct_sales_contract ADD CONSTRAINT chk_sales_contract_status
    CHECK (status IN ('草稿', '执行中', '已签署', '已归档')) NOT VALID;

ALTER TABLE ct_purchase_contract DROP CONSTRAINT IF EXISTS chk_purchase_contract_status;
ALTER TABLE ct_purchase_contract ADD CONSTRAINT chk_purchase_contract_status
    CHECK (status IN ('草稿', '执行中', '已签署', '已归档')) NOT VALID;

-- Customer / Supplier Statements: 待确认 → 已确认
ALTER TABLE st_customer_statement DROP CONSTRAINT IF EXISTS chk_customer_stmt_status;
ALTER TABLE st_customer_statement ADD CONSTRAINT chk_customer_stmt_status
    CHECK (status IN ('待确认', '已确认')) NOT VALID;

ALTER TABLE st_supplier_statement DROP CONSTRAINT IF EXISTS chk_supplier_stmt_status;
ALTER TABLE st_supplier_statement ADD CONSTRAINT chk_supplier_stmt_status
    CHECK (status IN ('待确认', '已确认')) NOT VALID;

-- Freight Statement: 待审核 → 已审核
ALTER TABLE st_freight_statement DROP CONSTRAINT IF EXISTS chk_freight_stmt_status;
ALTER TABLE st_freight_statement ADD CONSTRAINT chk_freight_stmt_status
    CHECK (status IN ('待审核', '已审核')) NOT VALID;

-- Freight Statement sign_status: 未签署 → 已签署
ALTER TABLE st_freight_statement DROP CONSTRAINT IF EXISTS chk_sign_status;
ALTER TABLE st_freight_statement ADD CONSTRAINT chk_sign_status
    CHECK (sign_status IS NULL OR sign_status IN ('未签署', '已签署')) NOT VALID;

-- Freight Bill: 未审核 → 已审核
ALTER TABLE lg_freight_bill DROP CONSTRAINT IF EXISTS chk_freight_bill_status;
ALTER TABLE lg_freight_bill ADD CONSTRAINT chk_freight_bill_status
    CHECK (status IN ('未审核', '已审核')) NOT VALID;

-- Freight Bill delivery_status: 未送达 → 已送达
ALTER TABLE lg_freight_bill DROP CONSTRAINT IF EXISTS chk_delivery_status;
ALTER TABLE lg_freight_bill ADD CONSTRAINT chk_delivery_status
    CHECK (delivery_status IN ('未送达', '已送达')) NOT VALID;

-- Payment: 草稿 → 已付款
ALTER TABLE fm_payment DROP CONSTRAINT IF EXISTS chk_payment_status;
ALTER TABLE fm_payment ADD CONSTRAINT chk_payment_status
    CHECK (status IN ('草稿', '已付款')) NOT VALID;

-- Receipt: 草稿 → 已收款
ALTER TABLE fm_receipt DROP CONSTRAINT IF EXISTS chk_receipt_status;
ALTER TABLE fm_receipt ADD CONSTRAINT chk_receipt_status
    CHECK (status IN ('草稿', '已收款')) NOT VALID;

-- Invoice Issue: 草稿 → 已开票
ALTER TABLE fm_invoice_issue DROP CONSTRAINT IF EXISTS chk_invoice_issue_status;
ALTER TABLE fm_invoice_issue ADD CONSTRAINT chk_invoice_issue_status
    CHECK (status IN ('草稿', '已开票')) NOT VALID;

-- Invoice Receipt: 草稿 → 已收票
ALTER TABLE fm_invoice_receipt DROP CONSTRAINT IF EXISTS chk_invoice_receipt_status;
ALTER TABLE fm_invoice_receipt ADD CONSTRAINT chk_invoice_receipt_status
    CHECK (status IN ('草稿', '已收票')) NOT VALID;

-- Database Export Task: 排队中 → 执行中 → 已完成 / 失败 / 已过期
ALTER TABLE sys_database_export_task DROP CONSTRAINT IF EXISTS chk_export_task_status;
ALTER TABLE sys_database_export_task ADD CONSTRAINT chk_export_task_status
    CHECK (status IN ('排队中', '执行中', '已完成', '失败', '已过期')) NOT VALID;
