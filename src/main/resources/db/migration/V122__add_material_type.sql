-- 商品资料增加商品类型：实体商品 / 附加费用
-- 附加费用类商品复用 md_material 主数据体系（费用名称即品名），
-- 物理属性列保留但费用类行存空串/零值。
ALTER TABLE md_material ADD COLUMN material_type varchar(16);

UPDATE md_material SET material_type = '实体商品' WHERE material_type IS NULL;

ALTER TABLE md_material
    ALTER COLUMN material_type SET DEFAULT '实体商品',
    ALTER COLUMN material_type SET NOT NULL;

ALTER TABLE md_material
    ADD CONSTRAINT chk_md_material_type
        CHECK (material_type IN ('实体商品', '附加费用'));
