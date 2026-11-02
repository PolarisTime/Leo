-- V211: 物流对账单明细补「来源物流单日期」快照列，并回填历史数据。
--
-- 背景：物流对账单详情按「来源物流单」分组，组头需要显示来源物流单的「单据日期」。此前该日期
-- 只在读取时实时反查 lg_freight_bill 得到（FreightBillStatementSourceQueryService#findByBillIds
-- 过滤 deleted_flag = false），一旦来源物流单被软删除，详情里的日期就变成 null，前端只能显示「-」。
-- 因此改为保存时落快照（FreightStatementSourceService 写入 sourceFreightBillTime），历史数据由
-- 本脚本回填。
--
-- 列口径：lg_freight_bill.bill_time 是 timestamp，本列只保留日期部分（实体侧为 LocalDate），
--         回填时显式 ::date 截断，避免隐式转换在不同时区下偏移一天。
-- 幂等：ADD COLUMN IF NOT EXISTS；回填只更新 IS NULL 的行，可重复执行。

ALTER TABLE public.st_freight_statement_item
    ADD COLUMN IF NOT EXISTS source_freight_bill_time date;

COMMENT ON COLUMN public.st_freight_statement_item.source_freight_bill_time IS
    '来源物流单日期快照(lg_freight_bill.bill_time 的日期部分); 保存时由来源物流单决定, 客户端不改; V211 回填历史数据';

UPDATE public.st_freight_statement_item item
   SET source_freight_bill_time = bill.bill_time::date
  FROM public.lg_freight_bill bill
 WHERE bill.id = item.source_freight_bill_id
   AND item.source_freight_bill_time IS NULL;
