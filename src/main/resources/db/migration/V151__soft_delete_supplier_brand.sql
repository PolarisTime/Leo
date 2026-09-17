-- 供应商经营品牌改为软删除:
--   1) 新增 deleted_flag 标记, 既有数据默认 false, 不影响现网;
--   2) 原唯一约束 uk_supplier_brand(supplier_id, brand_name) 改为仅约束未删除行的
--      部分唯一索引, 允许软删后重新绑定同名品牌, 同时保留历史行以支持供应商恢复;
--   3) 软删供应商时应用层连带软删其品牌, 避免物理孤儿行。

ALTER TABLE public.md_supplier_brand
    ADD COLUMN deleted_flag boolean DEFAULT false NOT NULL;

ALTER TABLE public.md_supplier_brand
    DROP CONSTRAINT uk_supplier_brand;

CREATE UNIQUE INDEX uk_supplier_brand_active
    ON public.md_supplier_brand USING btree (supplier_id, brand_name)
    WHERE deleted_flag = false;

COMMENT ON COLUMN public.md_supplier_brand.deleted_flag IS '软删除标记, true 表示已删除; 仅 false 的行参与唯一性约束';
