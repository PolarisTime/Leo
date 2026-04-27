INSERT INTO st_freight_statement_item (
    id,
    statement_id,
    line_no,
    source_no,
    customer_name,
    project_name,
    material_code,
    material_name,
    brand,
    category,
    material,
    spec,
    length,
    quantity,
    piece_weight_ton,
    pieces_per_bundle,
    batch_no,
    weight_ton,
    warehouse_name,
    quantity_unit
)
SELECT
    700761000000000001,
    700760000000000001,
    1,
    '2026OB000003',
    '上海建工集团股份有限公司',
    '浦东新区商业综合体',
    'H-H300-300',
    'H型钢300*300',
    '莱钢',
    'H型钢',
    'Q235B',
    '300*300',
    '12米',
    10,
    1.200,
    1,
    'BATCH-260414-01',
    12.000,
    '马鞍山港口仓库',
    '件'
WHERE NOT EXISTS (
    SELECT 1
    FROM st_freight_statement_item
    WHERE statement_id = 700760000000000001
);

INSERT INTO st_freight_statement_item (
    id,
    statement_id,
    line_no,
    source_no,
    customer_name,
    project_name,
    material_code,
    material_name,
    brand,
    category,
    material,
    spec,
    length,
    quantity,
    piece_weight_ton,
    pieces_per_bundle,
    batch_no,
    weight_ton,
    warehouse_name,
    quantity_unit
)
SELECT
    700761000000000002,
    700760000000000002,
    1,
    '2026OB000004',
    '安徽建工集团有限公司',
    '合肥地铁4号线',
    'RB-H400-16-9',
    '螺纹钢16',
    '永钢',
    '螺纹钢',
    'HRB400',
    '16',
    '9米',
    4,
    1.580,
    112,
    'BATCH-260418-03',
    6.320,
    '合肥瑶海钢材市场',
    '件'
WHERE NOT EXISTS (
    SELECT 1
    FROM st_freight_statement_item
    WHERE statement_id = 700760000000000002
);
