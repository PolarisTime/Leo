-- 商品资料增加启停状态：正常 / 禁用。
--
-- 口径：
--   * 库里存中文 label（与 md_supplier / md_material_category 的 status 一致），
--     默认「正常」，历史数据由 DEFAULT 一次性回填，不存在 NULL 行。
--   * 禁用只影响「给用户选商品」的查询（行情匹配、单据选商品下拉），不删数据、
--     不改历史单据快照，历史单据仍按快照回显。
--   * 唯一性/导入查重（品牌+材质+规格+长度 identity）不使用 status 过滤，
--     禁用商品仍参与查重，避免同一 identity 重复建档。
ALTER TABLE md_material ADD COLUMN IF NOT EXISTS status varchar(16) NOT NULL DEFAULT '正常';

CREATE INDEX IF NOT EXISTS idx_md_material_status ON md_material (status);

COMMENT ON COLUMN md_material.status IS '商品状态: 正常 / 禁用; 禁用不删除数据, 仅从选商品查询中排除';
