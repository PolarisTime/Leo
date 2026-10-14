-- 关键字检索索引补齐: 收付款/物流单三个关键字列缺少裸列 trigram 索引。
--
-- 背景: 列表页关键字一直是 `lower(col) LIKE '%kw%'`, 而 V1 基线(以及后续迁移)里的关键字索引全部建在
-- 裸列上且带 partial 条件 `WHERE deleted_flag = false`(如 idx_fm_receipt_receipt_no_trgm):
--   CREATE INDEX ... ON public.fm_receipt USING gin (receipt_no public.gin_trgm_ops) WHERE (deleted_flag = false);
-- `lower(col)` 是另一个表达式, 与裸列索引表达式不匹配, 关键字一非空就走顺序扫描, 这些索引等于白建。
-- 查询侧已改为裸列 `ILIKE`(Specs#containsIgnoreCase), 因此本迁移只负责把缺失的裸列索引补齐,
-- 让 ILIKE 能命中 pg_trgm 的 gin_trgm_ops(支持 ~~* 运算符)。
--
-- 本迁移只做新增索引, 不改表结构、不改数据:
--   1) lg_freight_bill.carrier_code —— 物流单列表关键字字段(FreightBillService.SEARCH_FIELDS)之一,
--      现有 idx_lg_freight_bill_carrier_code_date 是 btree, 无法支撑 `%kw%` 包含匹配;
--   2) fm_receipt.counterparty_code / fm_receipt.counterparty_name —— 收款单列表关键字字段
--      (ReceiptService.page)之一, 现无任何 trgm 索引。
--
-- 刻意不建: fm_payment.business_type。业务类型只有少数几个枚举值, 关键字命中率接近全表,
-- trigram 索引对高命中率谓词不会被选中, 建了只是增加写放大, 故不补。

CREATE INDEX idx_lg_freight_bill_carrier_code_trgm
    ON public.lg_freight_bill USING gin (carrier_code public.gin_trgm_ops)
    WHERE (deleted_flag = false);

CREATE INDEX idx_fm_receipt_counterparty_code_trgm
    ON public.fm_receipt USING gin (counterparty_code public.gin_trgm_ops)
    WHERE (deleted_flag = false);

CREATE INDEX idx_fm_receipt_counterparty_name_trgm
    ON public.fm_receipt USING gin (counterparty_name public.gin_trgm_ops)
    WHERE (deleted_flag = false);
