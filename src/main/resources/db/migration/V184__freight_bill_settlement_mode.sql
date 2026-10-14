-- 物流单结算方式: 支持「按单价×吨位」与「按固定运费」两种结算方式。
--
-- 背景: 原先 lg_freight_bill 只有 unit_price / total_weight / total_freight 三个金额相关列,
-- 且 total_freight 恒等于 unit_price × total_weight(见 FreightBillApplyService)。实际业务存在
-- 以固定运费结算的情形, 因此新增「结算方式」与「固定运费金额」两列。
--
-- 口径(必须与 FreightBillService / FreightBillApplyService 保持一致):
--   · settlement_mode = 'BY_TONNAGE'(历史默认): 结算额 = unit_price × total_weight, 与迁移前完全一致;
--   · settlement_mode = 'FIXED': 结算额 = fixed_freight_amount(必填且 > 0); 此时 unit_price 与
--     total_weight 仍照旧存储与展示(运费对账单/财务概览/导出还在读吨位、单价), 但不再作为结算额;
--   · total_freight 始终是「本次结算金额」: BY_TONNAGE 时 = 单价 × 吨位, FIXED 时 = 固定运费金额。
--     对账单(FreightStatementSourceService 汇总 total_freight)、财务概览(SUM(total_freight))、
--     导出与列表排序等既有读取方继续读 total_freight 即可拿到正确的应付金额, 无需改动。
--
-- 向后兼容: 既有行一律按 BY_TONNAGE 结算(NOT NULL + DEFAULT 'BY_TONNAGE'), 历史金额数值不变,
-- fixed_freight_amount 保持 NULL。
--
-- 约束取舍: 结算方式取值与「FIXED ⇔ 金额非空」的一致性由服务层校验并返回 422(消息可读), 不落成
-- 数据库 CHECK —— 与「先加列、再放开校验」的兼容发布节奏冲突(避免应用版本回滚时数据库不兼容),
-- 且 status 等既有枚举列也未加 CHECK。

ALTER TABLE public.lg_freight_bill
    ADD COLUMN settlement_mode character varying(16) NOT NULL DEFAULT 'BY_TONNAGE',
    ADD COLUMN fixed_freight_amount numeric(14,2);

COMMENT ON COLUMN public.lg_freight_bill.settlement_mode IS
    '结算方式: BY_TONNAGE=按「单价×吨位」结算(历史默认), FIXED=按固定运费金额结算; 两种模式互斥, FIXED 时 fixed_freight_amount 必填且>0。';

COMMENT ON COLUMN public.lg_freight_bill.fixed_freight_amount IS
    '固定运费结算金额(元, 2位小数): 仅 settlement_mode=FIXED 时非空, 并作为结算额写入 total_freight; BY_TONNAGE 时为 NULL。';

COMMENT ON COLUMN public.lg_freight_bill.total_freight IS
    '本次结算金额(元, 2位小数): settlement_mode=BY_TONNAGE 时为 单价×吨位, FIXED 时为 fixed_freight_amount; 运费对账单/财务概览直接读本列。';

COMMENT ON COLUMN public.lg_freight_bill.unit_price IS
    '运费单价(元/吨, 2位小数): 两种结算方式下都照旧存储与展示; FIXED 时仅作参考, 不参与结算额。';

COMMENT ON COLUMN public.lg_freight_bill.total_weight IS
    '总吨位(吨, 8位小数): 由明细行重量汇总, 两种结算方式下都照旧存储与展示。';
