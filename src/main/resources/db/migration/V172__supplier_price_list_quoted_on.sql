-- 供应商价格表: 新增业务「报价日期」quoted_on(用户可填, 默认当天)
-- 背景: 价格表已取消版本语义(R2.1), 表头只剩"这张表是什么时候报的价"这一个业务时间概念;
--       mk_supplier_price_list.updated_at 是系统"最后修改时刻"(审计列), 二者必须严格区分:
--       改价/改条目会让 updated_at 变, 但业务报价日期由用户填且默认当天, 不随改价漂移。
-- 说明:
--   1) 历史行: 用 COALESCE(updated_at, created_at) 的日期回填, 与既有展示口径最接近;
--   2) 列最终 NOT NULL + DEFAULT CURRENT_DATE: 新行缺省 = 当天(应用层未显式传 quotedOn 时同口径);
--   3) 允许同一 (供应商, 品牌) 反复改 quoted_on(仅一个日期字段, 不产生版本)。

ALTER TABLE public.mk_supplier_price_list
    ADD COLUMN quoted_on date;

UPDATE public.mk_supplier_price_list
SET quoted_on = COALESCE(updated_at::date, created_at::date, CURRENT_DATE)
WHERE quoted_on IS NULL;

ALTER TABLE public.mk_supplier_price_list
    ALTER COLUMN quoted_on SET DEFAULT CURRENT_DATE,
    ALTER COLUMN quoted_on SET NOT NULL;

COMMENT ON COLUMN public.mk_supplier_price_list.quoted_on IS
    '业务报价日期(用户可填, 默认当天); 与系统审计列 updated_at(最后修改时刻)严格区分, 不参与版本语义';

-- 列表排序白名单新增 quotedOn(缺省仍按 updatedAt DESC); 按 (deleted_flag, quoted_on) 建索引支撑排序
CREATE INDEX idx_supplier_price_list_quoted_on
    ON public.mk_supplier_price_list USING btree (quoted_on DESC)
    WHERE deleted_flag = false;
