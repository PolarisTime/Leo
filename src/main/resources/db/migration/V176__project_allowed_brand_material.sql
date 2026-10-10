-- 项目资料新增「允许品牌」「允许材质」: 项目级可采购范围(销售订单导入采购订单明细时的匹配预警依据)。
-- 背景: 销售订单可从采购订单/采购入库导入明细, 但项目本身对可采品牌与材质常有约定;
--       此前没有任何项目级白名单, 只能靠人工核对, 导入越界品牌/材质不会被提醒。
-- 说明:
--   1) 两个字段都是逗号分隔的多值文本(与既有 products/designated_brands/supplier_freights 的约定一致),
--      数据库只存原样文本, 应用层负责去空白、去重与空值归一;
--   2) 空值表示「不限制」: 未配置的项目导入行为与改造前完全一致, 只在配置了白名单时才做匹配预警;
--   3) 品牌取值来自 md_material.brand(实体商品品牌去重), 材质取值来自 md_material.material(材质/牌号);
--      不做外键约束, 允许项目先配置再补商品主数据;
--   4) 预警不阻断导入: 匹配只用于提示人工复核, 是否放行由业务判断。

ALTER TABLE public.md_project
    ADD COLUMN allowed_brands character varying(512),
    ADD COLUMN allowed_materials character varying(512);

COMMENT ON COLUMN public.md_project.allowed_brands IS
    '允许的品牌(逗号分隔, 来自 md_material.brand); 空表示不限制';
COMMENT ON COLUMN public.md_project.allowed_materials IS
    '允许的材质(逗号分隔, 来自 md_material.material); 空表示不限制';
