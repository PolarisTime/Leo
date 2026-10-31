-- 历史遗留数据收口（V205）。
--
-- 背景：2026-10-31 全量遗留审计（.local/len-audit/legacy-data.md）发现的四类问题：
--   1) 10 个 CHECK 约束建表时带 NOT VALID，从未验证存量（实测 0 违规）→ 可直接 VALIDATE；
--   2) 6 张已删除的采购入库单在软删除时明细被物理释放，但头金额/重量未回写，
--      形成「有金额无明细」的幽灵单据（合计 339,142.47 元 / 46.577 吨）；
--   3) 操作日志分区表 sys_operation_log 预建到 2036 年，116 个空分区占 18MB/1,822 个
--      目录对象；V81 遗留的 sys_operation_log_unpartitioned 与分区表完全重复（2,949 行）；
--   4) 3 张零行零代码引用的孤儿表；2 条指向退役归档表的死外键（恢复历史备份时会复活
--      上一轮修掉的阻塞缺陷）。
--
-- 幂等：VALIDATE 可重复；UPDATE 带条件；DROP 全部 IF EXISTS；分区清理先数行再删。

-- ============ 1) 校验 10 个 NOT VALID CHECK（存量 0 违规，直接收口）============
ALTER TABLE public.po_purchase_order         VALIDATE CONSTRAINT chk_po_status;
ALTER TABLE public.po_purchase_inbound        VALIDATE CONSTRAINT chk_inbound_status;
ALTER TABLE public.st_customer_statement      VALIDATE CONSTRAINT chk_customer_stmt_status;
ALTER TABLE public.st_freight_statement       VALIDATE CONSTRAINT chk_sign_status;
ALTER TABLE public.ct_purchase_contract       VALIDATE CONSTRAINT chk_purchase_contract_status;
ALTER TABLE public.ct_sales_contract          VALIDATE CONSTRAINT chk_sales_contract_status;
ALTER TABLE public.fm_invoice_issue           VALIDATE CONSTRAINT chk_invoice_issue_status;
ALTER TABLE public.fm_invoice_receipt         VALIDATE CONSTRAINT chk_invoice_receipt_status;
ALTER TABLE public.st_supplier_statement      VALIDATE CONSTRAINT chk_supplier_stmt_status;

-- ============ 2) 幽灵已删入库单：明细已释放，头金额/重量归零 ============
UPDATE public.po_purchase_inbound head
   SET total_amount = 0,
       total_weight = 0
 WHERE head.deleted_flag = true
   AND NOT EXISTS (SELECT 1 FROM public.po_purchase_inbound_item item WHERE item.inbound_id = head.id)
   AND (head.total_amount <> 0 OR head.total_weight <> 0);

-- ============ 3) 清理重复日志副本表 + 孤儿表 ============
-- sys_operation_log_unpartitioned 是 V81 分区化时改名的旧表，数据已全量复制到分区表，
-- 代码中无任何引用（仅 V81 自身引用），保留只会造成 2,949 行重复与备份混淆。
DROP TABLE IF EXISTS public.sys_operation_log_unpartitioned;

-- 零行、零代码引用（LSP + grep 双证）的孤儿表。
-- 注意：ct_contract_purchase_order 虽然也是 0 行且无 Java 写路径，但被视图
-- global_search_document 引用（全局搜索仍会扫它），因此**不删**，保留为历史查询资产。
DROP TABLE IF EXISTS public.mk_supplier_price_adjustment_item;
DROP TABLE IF EXISTS public.mk_supplier_price_adjustment;
DROP TABLE IF EXISTS public.sys_database_export_task;

-- ============ 4) 指向退役归档表的死外键 ============
-- 退役表（COMMENT 标注「功能已退役」）已无写路径，但活跃/半退役表仍持有指向它们的外键；
-- 恢复历史备份时这些约束会重新参与校验，把上一轮修掉的明细阻塞缺陷带回来。只删约束、保留列。
ALTER TABLE public.fm_payment_allocation
    DROP CONSTRAINT IF EXISTS fk_fm_payment_allocation_supplier_statement;
ALTER TABLE public.fm_supplier_refund_receipt
    DROP CONSTRAINT IF EXISTS fk_fm_supplier_refund_receipt_refund;

-- ============ 5) 清理超窗口的空日志分区 ============
-- 保留「当前月 ±12 个月」，其余空分区删除（非空分区一律不动）。
-- 插入若超出保留窗口会落到 sys_operation_log_default，不影响可用性。
DO $$
DECLARE
    partition_name text;
    partition_rows bigint;
    keep_from date := date_trunc('month', CURRENT_DATE) - INTERVAL '12 months';
    keep_to   date := date_trunc('month', CURRENT_DATE) + INTERVAL '13 months';
BEGIN
    FOR partition_name IN
        SELECT child.relname
          FROM pg_inherits inh
          JOIN pg_class child ON child.oid = inh.inhrelid
          JOIN pg_class parent ON parent.oid = inh.inhparent
         WHERE parent.relname = 'sys_operation_log'
           AND child.relname ~ '^sys_operation_log_y[0-9]{4}m[0-9]{2}$'
    LOOP
        DECLARE
            part_month date := to_date(
                    regexp_replace(partition_name, '^sys_operation_log_y|m', '', 'g'),
                    'YYYYMM');
        BEGIN
            IF part_month < keep_from OR part_month >= keep_to THEN
                EXECUTE format('SELECT count(*) FROM public.%I', partition_name) INTO partition_rows;
                IF partition_rows = 0 THEN
                    EXECUTE format('DROP TABLE IF EXISTS public.%I', partition_name);
                END IF;
            END IF;
        END;
    END LOOP;
END
$$;
