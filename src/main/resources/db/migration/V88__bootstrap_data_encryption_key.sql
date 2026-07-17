INSERT INTO public.sys_security_secret (
    id,
    secret_type,
    secret_name,
    key_version,
    secret_value,
    status,
    activated_at,
    remark,
    created_by,
    created_name,
    created_at,
    deleted_flag
)
SELECT
    seed.next_id,
    'DATA_MASTER',
    '数据加密主密钥',
    1,
    replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', ''),
    'ACTIVE',
    CURRENT_TIMESTAMP,
    'Flyway 自动初始化，仅供内部数据加密使用',
    0,
    'flyway',
    CURRENT_TIMESTAMP,
    false
FROM (
    SELECT COALESCE(MAX(id), 700500000000000000) + 1 AS next_id
    FROM public.sys_security_secret
) seed
WHERE NOT EXISTS (
    SELECT 1
    FROM public.sys_security_secret
    WHERE secret_type = 'DATA_MASTER'
      AND status = 'ACTIVE'
      AND deleted_flag = false
);
