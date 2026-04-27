CREATE TABLE IF NOT EXISTS sys_attachment_binding (
    id BIGINT PRIMARY KEY,
    module_key VARCHAR(64) NOT NULL,
    record_id BIGINT NOT NULL,
    attachment_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_sys_attachment_binding_record
    ON sys_attachment_binding (module_key, record_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_attachment_binding_ref
    ON sys_attachment_binding (module_key, record_id, attachment_id);

INSERT INTO sys_attachment_binding (id, module_key, record_id, attachment_id, sort_order)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_attachment_binding) + ROW_NUMBER() OVER (ORDER BY fs.id, token.ord),
    'freight-statements',
    fs.id,
    CAST(token.attachment_id AS BIGINT),
    token.ord
FROM st_freight_statement fs
CROSS JOIN LATERAL regexp_split_to_table(COALESCE(fs.attachment_ids, ''), '\s*,\s*') WITH ORDINALITY AS token(attachment_id, ord)
WHERE fs.deleted_flag = FALSE
  AND token.attachment_id <> ''
  AND token.attachment_id ~ '^[0-9]+$'
ON CONFLICT (module_key, record_id, attachment_id) DO NOTHING;
