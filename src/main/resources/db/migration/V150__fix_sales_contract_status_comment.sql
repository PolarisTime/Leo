-- 修正销售合同状态注释漂移：V147 的 COMMENT 仍写 草稿 / 已审核 / 已发出 / 归档 / 作废，
-- 实际定稿口径（StatusConstants 销售合同状态机）为 草稿 / 审核 / 签发 / 归档 / 作废。
-- 仅更新注释，不改动表结构；COMMENT ON 语句本身幂等，可重复执行。
COMMENT ON TABLE public.so_sales_contract IS
    '销售合同: 项目级金额/吨位额度, 无明细表';
COMMENT ON COLUMN public.so_sales_contract.status IS
    '状态: 草稿 / 审核 / 签发 / 归档 / 作废';
