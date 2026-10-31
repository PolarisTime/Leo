-- V208: 重建 global_search_document（去掉 3 个 deprecated 列依赖），删索引重建，再删除 3 列。
--
-- !!! 发布顺序要求：必须与代码整改同批发布 !!!
--   HEAD（e261e0bb）中这 3 列仍有 JPA 映射，直接执行本迁移会让应用查询报
--   “column ... does not exist”：
--     * FreightBill.customerName / projectName（lg_freight_bill）
--     * FreightStatement.signStatus（st_freight_statement）
--   2026-10-31 16:39 工作区快照显示代码整改已在进行（尚未提交）：上述 @Column 映射，
--   FreightBillService 的 setCustomerName/setProjectName、FreightBillStatementSourceQueryService
--   的 getCustomerName/getProjectName、FreightStatementApplyService 的 setSignStatus，
--   以及 ModuleExportCatalog 中 customer_name/project_name/sign_status 三个导出列均已删除。
--   发布要求：包含这些代码改动的版本先上线（或与本迁移同一 release），再执行本迁移；
--   执行前用发布包确认实体已无对应 @Column 映射。
--   如代码整改排期更晚，可按文末「可拆分为两步」先上视图 + 索引（第一步不删列，应用安全）。
--
-- 视图改造方式（CREATE OR REPLACE VIEW，不改列形状）：
--   迁移前定义取自 dev 库 pg_get_viewdef('public.global_search_document'::regclass, true)
--   （16423 字符 / 214 行；快照见 .local/leo-migration-drafts/viewdef-current.sql）。
--   这 3 列并不是视图输出列，而是 freight_bill / freight_statement 两个 UNION ALL 分支里
--   summary 与 search_text 的 concat_ws 参数；因此把取值原位替换为 NULL::text（共 5 处），
--   其余定义逐字保留：
--     1) freight_bill.summary:      NULLIF(btrim(freight_bill.customer_name::text), '')  -> NULL::text
--     2) freight_bill.search_text:  freight_bill.customer_name, freight_bill.project_name
--                                   -> NULL::text, NULL::text
--     3) freight_statement.summary: NULLIF(btrim(freight_statement.sign_status::text), '')
--                                   -> NULL::text
--     4) freight_statement.search_text: freight_statement.sign_status -> NULL::text
--   concat_ws 会跳过 NULL 参数，故 summary/search_text 仍为 text，视图输出 8 列
--   （module_key, record_id, primary_no, summary, search_text, created_by, updated_at,
--   deleted_flag）的名字/顺序/类型完全不变 → CREATE OR REPLACE VIEW 合法，
--   且保留 OID/owner/ACL/依赖方，无需 DROP+CREATE，也无需重发 GRANT。
--
-- 索引重建：
--   idx_lg_freight_bill_list 是 INCLUDE (carrier_name, customer_name, project_name,
--   total_weight, total_freight) 的覆盖索引；DROP COLUMN 会连带删除整个索引
--   （INCLUDE 列同样产生依赖），因此先显式 DROP 再用去掉 customer_name/project_name
--   的定义重建，避免物流单列表页查询计划退化。
--   idx_lg_freight_bill_customer_name_trgm 是单列 GIN(trgm)，随 customer_name 删除，无需处理。
--
-- 幂等性：
--   CREATE OR REPLACE VIEW / DROP INDEX IF EXISTS / CREATE INDEX（前置 DROP 同名）/
--   ALTER TABLE ... DROP COLUMN IF EXISTS 均可重复执行；Flyway 事务包裹，失败整体回滚。
--
-- 回滚（只能恢复结构，历史取值不可恢复）：
--   ALTER TABLE public.lg_freight_bill ADD COLUMN customer_name varchar(128);
--   ALTER TABLE public.lg_freight_bill ADD COLUMN project_name varchar(200);
--   ALTER TABLE public.st_freight_statement ADD COLUMN sign_status varchar(16);
--   UPDATE public.st_freight_statement SET sign_status = '未签署' WHERE sign_status IS NULL;
--   ALTER TABLE public.st_freight_statement ALTER COLUMN sign_status SET NOT NULL;
--   ALTER TABLE public.st_freight_statement ADD CONSTRAINT chk_sign_status
--       CHECK (sign_status IS NULL
--              OR sign_status::text = ANY (ARRAY['未签署'::text, '已签署'::text]));
--   CREATE INDEX idx_lg_freight_bill_customer_name_trgm ON public.lg_freight_bill
--       USING gin (customer_name public.gin_trgm_ops) WHERE (deleted_flag = false);
--   DROP INDEX IF EXISTS public.idx_lg_freight_bill_list;
--   CREATE INDEX idx_lg_freight_bill_list ON public.lg_freight_bill
--       USING btree (status, bill_time DESC, id DESC)
--       INCLUDE (carrier_name, customer_name, project_name, total_weight, total_freight)
--       WHERE (deleted_flag = false);
--   -- 最后按迁移前快照恢复视图（viewdef-current.sql / git 中 V1__baseline.sql:859 起定义）：
--   CREATE OR REPLACE VIEW public.global_search_document AS <迁移前定义>;
--
-- 可拆分为两步（若代码整改排期晚于本迁移）：
--   第一步只执行「视图 + 索引」两段；第二步再执行文件末尾 3 个 DROP COLUMN。
--
-- 风险：
--   * 目标环境视图定义若与 dev 不一致，禁止直接套用；执行前需 diff 目标库
--     pg_get_viewdef 与本文件定义。
--   * CREATE INDEX 非 CONCURRENTLY（Flyway 事务内），执行期短暂阻塞 lg_freight_bill 写入；
--     dev 约 149 行，生产需按实际行数评估发布窗口。
--   * 删除后 summary/search_text 不再包含历史客户名/项目名/签署状态，全局搜索对这些
--     字段的命中消失（deprecated 的预期语义），输出契约不变。

-- pg_get_viewdef(pretty=true) 输出的表名未带 public. 前缀，固定 search_path 保证解析确定。
-- （Flyway 以事务执行；若手工 psql 执行，请先显式 BEGIN。）
SET LOCAL search_path TO public;

CREATE OR REPLACE VIEW public.global_search_document AS
 WITH freight_bill_sources AS (
         SELECT item.bill_id AS record_id,
            string_agg(DISTINCT btrim(item.source_no::text), ', '::text ORDER BY (btrim(item.source_no::text))) AS source_nos
           FROM lg_freight_bill_item item
          WHERE COALESCE(btrim(item.source_no::text), ''::text) <> ''::text
          GROUP BY item.bill_id
        ), supplier_statement_sources AS (
         SELECT item.statement_id AS record_id,
            string_agg(DISTINCT btrim(item.source_no::text), ', '::text ORDER BY (btrim(item.source_no::text))) AS source_nos
           FROM st_supplier_statement_item item
          WHERE COALESCE(btrim(item.source_no::text), ''::text) <> ''::text
          GROUP BY item.statement_id
        ), customer_statement_sources AS (
         SELECT item.statement_id AS record_id,
            string_agg(DISTINCT btrim(item.source_no::text), ', '::text ORDER BY (btrim(item.source_no::text))) AS source_nos
           FROM st_customer_statement_item item
          WHERE COALESCE(btrim(item.source_no::text), ''::text) <> ''::text
          GROUP BY item.statement_id
        ), freight_statement_sources AS (
         SELECT item.statement_id AS record_id,
            string_agg(DISTINCT btrim(item.source_no::text), ', '::text ORDER BY (btrim(item.source_no::text))) AS source_nos
           FROM st_freight_statement_item item
          WHERE COALESCE(btrim(item.source_no::text), ''::text) <> ''::text
          GROUP BY item.statement_id
        ), purchase_contract_sources AS (
         SELECT relation.contract_id AS record_id,
            string_agg(DISTINCT btrim(purchase_order.order_no::text), ', '::text ORDER BY (btrim(purchase_order.order_no::text))) AS source_nos
           FROM ct_contract_purchase_order relation
             JOIN po_purchase_order purchase_order ON purchase_order.id = relation.purchase_order_id AND purchase_order.deleted_flag = false
          WHERE COALESCE(btrim(purchase_order.order_no::text), ''::text) <> ''::text
          GROUP BY relation.contract_id
        ), invoice_receipt_sources AS (
         SELECT source_no.record_id,
            string_agg(DISTINCT source_no.value, ', '::text ORDER BY source_no.value) AS source_nos
           FROM ( SELECT relation.receipt_id AS record_id,
                    btrim(purchase_order.order_no::text) AS value
                   FROM fm_invoice_receipt_source_order relation
                     JOIN po_purchase_order purchase_order ON purchase_order.id = relation.purchase_order_id AND purchase_order.deleted_flag = false
                UNION ALL
                 SELECT item.receipt_id AS record_id,
                    btrim(item.source_no::text) AS value
                   FROM fm_invoice_receipt_item item) source_no
          WHERE COALESCE(source_no.value, ''::text) <> ''::text
          GROUP BY source_no.record_id
        ), invoice_issue_sources AS (
         SELECT source_no.record_id,
            string_agg(DISTINCT source_no.value, ', '::text ORDER BY source_no.value) AS source_nos
           FROM ( SELECT relation.issue_id AS record_id,
                    btrim(sales_order.order_no::text) AS value
                   FROM fm_invoice_issue_source_order relation
                     JOIN so_sales_order sales_order ON sales_order.id = relation.sales_order_id AND sales_order.deleted_flag = false
                UNION ALL
                 SELECT item.issue_id AS record_id,
                    btrim(item.source_no::text) AS value
                   FROM fm_invoice_issue_item item) source_no
          WHERE COALESCE(source_no.value, ''::text) <> ''::text
          GROUP BY source_no.record_id
        )
 SELECT 'purchase-order'::text AS module_key,
    purchase_order.id AS record_id,
    purchase_order.order_no::text AS primary_no,
    concat_ws(' / '::text, NULLIF(btrim(purchase_order.supplier_name::text), ''::text), NULLIF(btrim(purchase_order.buyer_name::text), ''::text), NULLIF(btrim(purchase_order.status::text), ''::text)) AS summary,
    concat_ws(' '::text, purchase_order.id::text, purchase_order.order_no, purchase_order.supplier_name, purchase_order.buyer_name, purchase_order.status, purchase_order.remark) AS search_text,
    purchase_order.created_by,
    COALESCE(purchase_order.updated_at, purchase_order.created_at) AS updated_at,
    purchase_order.deleted_flag
   FROM po_purchase_order purchase_order
UNION ALL
 SELECT 'purchase-inbound'::text AS module_key,
    inbound.id AS record_id,
    inbound.inbound_no::text AS primary_no,
    concat_ws(' / '::text, NULLIF(btrim(inbound.supplier_name::text), ''::text), NULLIF(btrim(inbound.warehouse_name::text), ''::text), NULLIF(btrim(inbound.status::text), ''::text)) AS summary,
    concat_ws(' '::text, inbound.id::text, inbound.inbound_no, inbound.purchase_order_no, inbound.supplier_name, inbound.warehouse_name, inbound.settlement_mode, inbound.status, inbound.remark) AS search_text,
    inbound.created_by,
    COALESCE(inbound.updated_at, inbound.created_at) AS updated_at,
    inbound.deleted_flag
   FROM po_purchase_inbound inbound
UNION ALL
 SELECT 'sales-order'::text AS module_key,
    sales_order.id AS record_id,
    sales_order.order_no::text AS primary_no,
    concat_ws(' / '::text, NULLIF(btrim(sales_order.customer_name::text), ''::text), NULLIF(btrim(sales_order.project_name::text), ''::text), NULLIF(btrim(sales_order.status::text), ''::text)) AS summary,
    concat_ws(' '::text, sales_order.id::text, sales_order.order_no, sales_order.purchase_order_no, sales_order.purchase_inbound_no, sales_order.customer_code, sales_order.customer_name, sales_order.project_id::text, sales_order.project_name, sales_order.sales_name, sales_order.status, sales_order.remark) AS search_text,
    sales_order.created_by,
    COALESCE(sales_order.updated_at, sales_order.created_at) AS updated_at,
    sales_order.deleted_flag
   FROM so_sales_order sales_order
UNION ALL
 SELECT 'sales-outbound'::text AS module_key,
    outbound.id AS record_id,
    outbound.outbound_no::text AS primary_no,
    concat_ws(' / '::text, NULLIF(btrim(outbound.customer_name::text), ''::text), NULLIF(btrim(outbound.project_name::text), ''::text), NULLIF(btrim(outbound.status::text), ''::text)) AS summary,
    concat_ws(' '::text, outbound.id::text, outbound.outbound_no, outbound.sales_order_no, outbound.customer_name, outbound.project_name, outbound.warehouse_name, outbound.status, outbound.remark) AS search_text,
    outbound.created_by,
    COALESCE(outbound.updated_at, outbound.created_at) AS updated_at,
    outbound.deleted_flag
   FROM so_sales_outbound outbound
UNION ALL
 SELECT 'freight-bill'::text AS module_key,
    freight_bill.id AS record_id,
    freight_bill.bill_no::text AS primary_no,
    concat_ws(' / '::text, NULLIF(btrim(freight_bill.carrier_name::text), ''::text), NULL::text, NULLIF(btrim(freight_bill.status::text), ''::text)) AS summary,
    concat_ws(' '::text, freight_bill.id::text, freight_bill.bill_no, freight_bill_sources.source_nos, freight_bill.carrier_name, freight_bill.vehicle_plate, NULL::text, NULL::text, freight_bill.status, freight_bill.remark) AS search_text,
    freight_bill.created_by,
    COALESCE(freight_bill.updated_at, freight_bill.created_at) AS updated_at,
    freight_bill.deleted_flag
   FROM lg_freight_bill freight_bill
     LEFT JOIN freight_bill_sources ON freight_bill_sources.record_id = freight_bill.id
UNION ALL
 SELECT 'purchase-contract'::text AS module_key,
    purchase_contract.id AS record_id,
    purchase_contract.contract_no::text AS primary_no,
    concat_ws(' / '::text, NULLIF(btrim(purchase_contract.supplier_name::text), ''::text), NULLIF(btrim(purchase_contract.buyer_name::text), ''::text), NULLIF(btrim(purchase_contract.status::text), ''::text)) AS summary,
    concat_ws(' '::text, purchase_contract.id::text, purchase_contract.contract_no, purchase_contract_sources.source_nos, purchase_contract.supplier_name, purchase_contract.buyer_name, purchase_contract.status, purchase_contract.remark) AS search_text,
    purchase_contract.created_by,
    COALESCE(purchase_contract.updated_at, purchase_contract.created_at) AS updated_at,
    purchase_contract.deleted_flag
   FROM ct_purchase_contract purchase_contract
     LEFT JOIN purchase_contract_sources ON purchase_contract_sources.record_id = purchase_contract.id
UNION ALL
 SELECT 'sales-contract'::text AS module_key,
    sales_contract.id AS record_id,
    sales_contract.contract_no::text AS primary_no,
    concat_ws(' / '::text, NULLIF(btrim(sales_contract.customer_name::text), ''::text), NULLIF(btrim(sales_contract.project_name::text), ''::text), NULLIF(btrim(sales_contract.status::text), ''::text)) AS summary,
    concat_ws(' '::text, sales_contract.id::text, sales_contract.contract_no, sales_contract.customer_name, sales_contract.project_name, sales_contract.sales_name, sales_contract.status, sales_contract.remark) AS search_text,
    sales_contract.created_by,
    COALESCE(sales_contract.updated_at, sales_contract.created_at) AS updated_at,
    sales_contract.deleted_flag
   FROM ct_sales_contract sales_contract
UNION ALL
 SELECT 'supplier-statement'::text AS module_key,
    supplier_statement.id AS record_id,
    supplier_statement.statement_no::text AS primary_no,
    concat_ws(' / '::text, NULLIF(btrim(supplier_statement.supplier_name::text), ''::text), NULLIF(btrim(supplier_statement.status::text), ''::text)) AS summary,
    concat_ws(' '::text, supplier_statement.id::text, supplier_statement.statement_no, supplier_statement_sources.source_nos, supplier_statement.supplier_code, supplier_statement.supplier_name, supplier_statement.status, supplier_statement.remark) AS search_text,
    supplier_statement.created_by,
    COALESCE(supplier_statement.updated_at, supplier_statement.created_at) AS updated_at,
    supplier_statement.deleted_flag
   FROM st_supplier_statement supplier_statement
     LEFT JOIN supplier_statement_sources ON supplier_statement_sources.record_id = supplier_statement.id
UNION ALL
 SELECT 'customer-statement'::text AS module_key,
    customer_statement.id AS record_id,
    customer_statement.statement_no::text AS primary_no,
    concat_ws(' / '::text, NULLIF(btrim(customer_statement.customer_name::text), ''::text), NULLIF(btrim(customer_statement.project_name::text), ''::text), NULLIF(btrim(customer_statement.status::text), ''::text)) AS summary,
    concat_ws(' '::text, customer_statement.id::text, customer_statement.statement_no, customer_statement_sources.source_nos, customer_statement.customer_code, customer_statement.customer_name, customer_statement.project_id::text, customer_statement.project_name, customer_statement.status, customer_statement.remark) AS search_text,
    customer_statement.created_by,
    COALESCE(customer_statement.updated_at, customer_statement.created_at) AS updated_at,
    customer_statement.deleted_flag
   FROM st_customer_statement customer_statement
     LEFT JOIN customer_statement_sources ON customer_statement_sources.record_id = customer_statement.id
UNION ALL
 SELECT 'freight-statement'::text AS module_key,
    freight_statement.id AS record_id,
    freight_statement.statement_no::text AS primary_no,
    concat_ws(' / '::text, NULLIF(btrim(freight_statement.carrier_name::text), ''::text), NULL::text, NULLIF(btrim(freight_statement.status::text), ''::text)) AS summary,
    concat_ws(' '::text, freight_statement.id::text, freight_statement.statement_no, freight_statement_sources.source_nos, freight_statement.carrier_code, freight_statement.carrier_name, NULL::text, freight_statement.status, freight_statement.remark) AS search_text,
    freight_statement.created_by,
    COALESCE(freight_statement.updated_at, freight_statement.created_at) AS updated_at,
    freight_statement.deleted_flag
   FROM st_freight_statement freight_statement
     LEFT JOIN freight_statement_sources ON freight_statement_sources.record_id = freight_statement.id
UNION ALL
 SELECT 'receipt'::text AS module_key,
    receipt.id AS record_id,
    receipt.receipt_no::text AS primary_no,
    concat_ws(' / '::text, NULLIF(btrim(receipt.customer_name::text), ''::text), NULLIF(btrim(receipt.project_name::text), ''::text), NULLIF(btrim(receipt.status::text), ''::text)) AS summary,
    concat_ws(' '::text, receipt.id::text, receipt.receipt_no, receipt.customer_code, receipt.customer_name, receipt.project_id::text, receipt.project_name, receipt.pay_type, receipt.operator_name, receipt.status, receipt.remark) AS search_text,
    receipt.created_by,
    COALESCE(receipt.updated_at, receipt.created_at) AS updated_at,
    receipt.deleted_flag
   FROM fm_receipt receipt
UNION ALL
 SELECT 'payment'::text AS module_key,
    payment.id AS record_id,
    payment.payment_no::text AS primary_no,
    concat_ws(' / '::text, NULLIF(btrim(payment.counterparty_name::text), ''::text), NULLIF(btrim(payment.business_type::text), ''::text), NULLIF(btrim(payment.status::text), ''::text)) AS summary,
    concat_ws(' '::text, payment.id::text, payment.payment_no, payment.business_type, payment.counterparty_code, payment.counterparty_name, payment.pay_type, payment.operator_name, payment.status, payment.remark) AS search_text,
    payment.created_by,
    COALESCE(payment.updated_at, payment.created_at) AS updated_at,
    payment.deleted_flag
   FROM fm_payment payment
UNION ALL
 SELECT 'invoice-receipt'::text AS module_key,
    invoice_receipt.id AS record_id,
    invoice_receipt.receive_no::text AS primary_no,
    concat_ws(' / '::text, NULLIF(btrim(invoice_receipt.supplier_name::text), ''::text), NULLIF(btrim(invoice_receipt.invoice_no::text), ''::text), NULLIF(btrim(invoice_receipt.status::text), ''::text)) AS summary,
    concat_ws(' '::text, invoice_receipt.id::text, invoice_receipt.receive_no, invoice_receipt.invoice_no, invoice_receipt_sources.source_nos, invoice_receipt.supplier_name, invoice_receipt.invoice_title, invoice_receipt.invoice_type, invoice_receipt.operator_name, invoice_receipt.status, invoice_receipt.remark) AS search_text,
    invoice_receipt.created_by,
    COALESCE(invoice_receipt.updated_at, invoice_receipt.created_at) AS updated_at,
    invoice_receipt.deleted_flag
   FROM fm_invoice_receipt invoice_receipt
     LEFT JOIN invoice_receipt_sources ON invoice_receipt_sources.record_id = invoice_receipt.id
UNION ALL
 SELECT 'invoice-issue'::text AS module_key,
    invoice_issue.id AS record_id,
    invoice_issue.issue_no::text AS primary_no,
    concat_ws(' / '::text, NULLIF(btrim(invoice_issue.customer_name::text), ''::text), NULLIF(btrim(invoice_issue.project_name::text), ''::text), NULLIF(btrim(invoice_issue.status::text), ''::text)) AS summary,
    concat_ws(' '::text, invoice_issue.id::text, invoice_issue.issue_no, invoice_issue.invoice_no, invoice_issue_sources.source_nos, invoice_issue.customer_name, invoice_issue.project_name, invoice_issue.invoice_type, invoice_issue.operator_name, invoice_issue.status, invoice_issue.remark) AS search_text,
    invoice_issue.created_by,
    COALESCE(invoice_issue.updated_at, invoice_issue.created_at) AS updated_at,
    invoice_issue.deleted_flag
   FROM fm_invoice_issue invoice_issue
     LEFT JOIN invoice_issue_sources ON invoice_issue_sources.record_id = invoice_issue.id
UNION ALL
 SELECT 'ledger-adjustment'::text AS module_key,
    ledger_adjustment.id AS record_id,
    ledger_adjustment.adjustment_no::text AS primary_no,
    concat_ws(' / '::text, NULLIF(btrim(ledger_adjustment.counterparty_name::text), ''::text), NULLIF(btrim(ledger_adjustment.project_name::text), ''::text), NULLIF(btrim(ledger_adjustment.status::text), ''::text)) AS summary,
    concat_ws(' '::text, ledger_adjustment.id::text, ledger_adjustment.adjustment_no, ledger_adjustment.direction, ledger_adjustment.counterparty_type, ledger_adjustment.counterparty_code, ledger_adjustment.counterparty_name, ledger_adjustment.project_id::text, ledger_adjustment.project_name, ledger_adjustment.adjustment_type, ledger_adjustment.effect, ledger_adjustment.operator_name, ledger_adjustment.status, ledger_adjustment.remark) AS search_text,
    ledger_adjustment.created_by,
    COALESCE(ledger_adjustment.updated_at, ledger_adjustment.created_at) AS updated_at,
    ledger_adjustment.deleted_flag
   FROM fm_ledger_adjustment ledger_adjustment
;

-- 覆盖索引：显式去掉 INCLUDE 中的 customer_name / project_name 后重建
DROP INDEX IF EXISTS public.idx_lg_freight_bill_list;
CREATE INDEX idx_lg_freight_bill_list
    ON public.lg_freight_bill USING btree (status, bill_time DESC, id DESC)
    INCLUDE (carrier_name, total_weight, total_freight)
    WHERE (deleted_flag = false);

-- 删除 3 个 deprecated 列（视图已不再引用；单列 GIN trgm 索引与列级 CHECK/NOT NULL 随列自动删除）
ALTER TABLE public.lg_freight_bill
    DROP COLUMN IF EXISTS customer_name;

ALTER TABLE public.lg_freight_bill
    DROP COLUMN IF EXISTS project_name;

ALTER TABLE public.st_freight_statement
    DROP COLUMN IF EXISTS sign_status;
