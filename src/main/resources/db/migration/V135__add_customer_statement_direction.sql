-- Phase 2 红字对账：客户对账单增加对账方向与来源销售退货单字段。
-- 蓝字为正常对账（正数金额），红字为退货冲销（负金额、自动确认、不占用来源明细、不参与收款核销）。
-- 历史数据统一按蓝字回填，direction 默认值即为蓝字。
--
-- 金额校验说明（已核对历史迁移）：
-- 基线 V1__baseline.sql 对 st_customer_statement 仅建立状态 CHECK(chk_customer_stmt_status)，
-- 未对 sales_amount / receipt_amount / closing_amount 建立任何非负 CHECK 约束；
-- V21、V23 及后续迁移也未新增金额约束。因此红字负数金额无需调整既有约束。
-- 蓝字金额非负、红字金额为负的校验在应用层（CustomerStatementRequest / StatementBalanceRule）执行。

ALTER TABLE public.st_customer_statement
    ADD COLUMN direction character varying(8) DEFAULT '蓝字'::character varying NOT NULL,
    ADD COLUMN source_sales_return_id bigint,
    ADD COLUMN source_sales_return_no character varying(64);

ALTER TABLE public.st_customer_statement
    ADD CONSTRAINT chk_customer_stmt_direction CHECK (direction IN ('蓝字', '红字'));

COMMENT ON COLUMN public.st_customer_statement.direction IS '对账方向：蓝字=正常对账；红字=退货冲销，金额为负。';
COMMENT ON COLUMN public.st_customer_statement.source_sales_return_id IS '红字对账单来源销售退货单ID；蓝字为空。';
COMMENT ON COLUMN public.st_customer_statement.source_sales_return_no IS '红字对账单来源销售退货单号快照；蓝字为空。';

CREATE INDEX idx_st_customer_statement_source_return
    ON public.st_customer_statement (source_sales_return_id);

-- 按来源退货单幂等：同一退货单只允许存在一条未删除的红字对账单。
CREATE UNIQUE INDEX uk_st_customer_statement_source_return_active
    ON public.st_customer_statement (source_sales_return_id)
    WHERE source_sales_return_id IS NOT NULL AND direction = '红字' AND deleted_flag = false;
