-- 补充 V184 两列的列注释: 明确「结算口径由服务层校验, 数据库没有 CHECK 兜底」。
--
-- 背景: V184 加了 settlement_mode / fixed_freight_amount 两列, 但列注释里没有写清「两模式互斥、
-- FIXED 金额必填且大于 0」这条口径由谁保证。实际由 FreightBillService#applySettlementTerms 校验并
-- 返回 422(消息可读), 数据库只有 NOT NULL DEFAULT(历史行安全)而没有 CHECK —— 后人若以为数据库会兜,
-- 就可能绕过服务层直接写库写出「FIXED 却没有金额」的矛盾行。
--
-- V184 已合入且已在环境应用, 按仓库迁移规则不得改写, 因此新增本迁移只更新这两条列注释:
-- 不改表结构、不改数据、不加约束。

COMMENT ON COLUMN public.lg_freight_bill.settlement_mode IS
    '结算方式: BY_TONNAGE=按「单价×吨位」结算(历史默认), FIXED=按固定运费金额结算; 两种模式互斥、FIXED 时 fixed_freight_amount 必填且>0, 该口径由服务层校验并返回 422, 数据库只有 NOT NULL 默认值(历史行安全), 没有 CHECK 兜底, 不得绕过服务层写矛盾行。';

COMMENT ON COLUMN public.lg_freight_bill.fixed_freight_amount IS
    '固定运费结算金额(元, 2位小数): 仅 settlement_mode=FIXED 时非空, 并作为结算额写入 total_freight; BY_TONNAGE 时为 NULL。金额必填且>0 由服务层校验(422), 非数据库约束。';
