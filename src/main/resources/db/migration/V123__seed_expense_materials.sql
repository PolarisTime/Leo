-- 预置常见附加费用主数据（复用 md_material，material_type='附加费用'）
-- 固定 ID 段 990001~990006（雪花段外显式 ID），幂等：已存在则跳过。
-- 费用类商品 brand/spec/length 存空串，身份四元组 (brand,material,spec,length)
-- 归一化后为 ('', 名称, '', '') 天然唯一。
INSERT INTO md_material (
    id, material_code, brand, material, category, spec, length,
    unit, quantity_unit, piece_weight_ton, pieces_per_bundle, unit_price,
    material_type, remark,
    created_by, created_name, created_at, deleted_flag
)
SELECT
    v.id,
    CAST(v.id AS varchar),
    '',
    v.charge_name,
    '附加费用',
    '',
    '',
    '次',
    '次',
    0,
    0,
    0,
    '附加费用',
    '系统预置费用项',
    0,
    'system',
    CURRENT_TIMESTAMP,
    FALSE
FROM (VALUES
    (990001, '运费'),
    (990002, '短途转运费'),
    (990003, '吊装费'),
    (990004, '加工费'),
    (990005, '过磅费'),
    (990006, '仓储杂费')
) AS v(id, charge_name)
WHERE NOT EXISTS (
    SELECT 1 FROM md_material m
    WHERE m.material_type = '附加费用'
      AND btrim(m.material) = v.charge_name
      AND m.deleted_flag = FALSE
);
