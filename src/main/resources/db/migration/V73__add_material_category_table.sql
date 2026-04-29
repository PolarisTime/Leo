-- Material category lookup table for dynamic category management.

CREATE TABLE IF NOT EXISTS md_material_category (
    id              BIGINT NOT NULL PRIMARY KEY,
    category_code   VARCHAR(32) NOT NULL,
    category_name   VARCHAR(64) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(16) NOT NULL DEFAULT '正常',
    remark          VARCHAR(255),
    deleted_flag    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    created_name    VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_name    VARCHAR(64) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_md_material_category_code_active
    ON md_material_category (category_code) WHERE deleted_flag = FALSE;

INSERT INTO md_material_category (id, category_code, category_name, sort_order, remark)
VALUES
    (700000000000000101, 'REBAR',      '螺纹钢', 1, 'OOBE 种子数据'),
    (700000000000000102, 'WIRE_ROD',   '盘螺',   2, 'OOBE 种子数据'),
    (700000000000000103, 'WIRE',       '线材',   3, 'OOBE 种子数据')
ON CONFLICT DO NOTHING;
