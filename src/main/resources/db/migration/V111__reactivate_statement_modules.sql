-- 对账模块重新启用：撤销 V96 的“历史资料”定位。
-- 业务口径（2026-07-26 评审确认）：对账单与收付款保持可选关联，不恢复结算强前置。

COMMENT ON TABLE public.st_customer_statement
    IS '客户对账单；已启用，收付款可选关联对账单核销';

COMMENT ON TABLE public.st_freight_statement
    IS '物流对账单；已启用，收付款可选关联对账单核销';
