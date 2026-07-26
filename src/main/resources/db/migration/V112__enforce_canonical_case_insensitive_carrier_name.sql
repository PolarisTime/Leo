-- 应用按 String.trim() 的 U+0000-U+0020 规则清理名称边界，并按 LOWER(name) 判断同名。
-- PostgreSQL text 不接受 U+0000，数据库侧显式清理可存储的 U+0001-U+0020，保持等价语义。

-- EXCLUSIVE 与 SELECT FOR UPDATE 的 ROW SHARE 冲突，但仍允许普通 SELECT 的 ACCESS SHARE。
-- 先等待既有锁事务结束，再阻断增删改及新的行锁，避免清洗期间形成锁升级等待环。
LOCK TABLE public.md_carrier IN EXCLUSIVE MODE;

DROP INDEX public.uk_md_carrier_carrier_name_active;

UPDATE public.md_carrier
SET carrier_name = REGEXP_REPLACE(
        carrier_name,
        '^[\x01-\x20]+|[\x01-\x20]+$',
        '',
        'g'
    ),
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE deleted_flag = FALSE
  AND carrier_name <> REGEXP_REPLACE(
        carrier_name,
        '^[\x01-\x20]+|[\x01-\x20]+$',
        '',
        'g'
    );

-- 保留每组中 id 最小的有效记录。其他记录使用稳定 ID 后缀改名；若后缀名称已存在，
-- 继续增加序号直至可用，避免一次性“名称-编码”改名产生二次碰撞。
DO $$
DECLARE
    duplicate_carrier RECORD;
    base_name TEXT;
    candidate_name TEXT;
    candidate_suffix TEXT;
    collision_attempt INTEGER;
BEGIN
    FOR duplicate_carrier IN
        SELECT ranked.id, ranked.carrier_name
        FROM (
            SELECT carrier.id,
                   carrier.carrier_name,
                   ROW_NUMBER() OVER (
                       PARTITION BY LOWER(carrier.carrier_name)
                       ORDER BY carrier.id
                   ) AS name_rank
            FROM public.md_carrier carrier
            WHERE carrier.deleted_flag = FALSE
        ) ranked
        WHERE ranked.carrier_name = ''
           OR ranked.name_rank > 1
        ORDER BY ranked.id
    LOOP
        base_name := COALESCE(NULLIF(duplicate_carrier.carrier_name, ''), '物流商');
        collision_attempt := 0;

        LOOP
            candidate_suffix := ' [' || duplicate_carrier.id::TEXT
                    || CASE
                           WHEN collision_attempt = 0 THEN ''
                           ELSE '-' || collision_attempt::TEXT
                       END
                    || ']';
            candidate_name := LEFT(
                    base_name,
                    GREATEST(0, 128 - CHAR_LENGTH(candidate_suffix))
            ) || candidate_suffix;

            EXIT WHEN NOT EXISTS (
                SELECT 1
                FROM public.md_carrier carrier
                WHERE carrier.deleted_flag = FALSE
                  AND carrier.id <> duplicate_carrier.id
                  AND LOWER(carrier.carrier_name) = LOWER(candidate_name)
            );

            collision_attempt := collision_attempt + 1;
        END LOOP;

        UPDATE public.md_carrier
        SET carrier_name = candidate_name,
            updated_by = 0,
            updated_name = 'flyway',
            updated_at = CURRENT_TIMESTAMP
        WHERE id = duplicate_carrier.id;
    END LOOP;
END
$$;

ALTER TABLE public.md_carrier
    ADD CONSTRAINT chk_md_carrier_active_name_canonical
    CHECK (
        deleted_flag
        OR (
            carrier_name = REGEXP_REPLACE(
                carrier_name,
                '^[\x01-\x20]+|[\x01-\x20]+$',
                '',
                'g'
            )
            AND carrier_name <> ''
        )
    );

CREATE UNIQUE INDEX uk_md_carrier_carrier_name_active
    ON public.md_carrier (LOWER(carrier_name))
    WHERE deleted_flag = FALSE;

COMMENT ON CONSTRAINT chk_md_carrier_active_name_canonical ON public.md_carrier IS
    '有效物流商名称必须去除首尾 U+0001-U+0020 边界字符且不能为空';

COMMENT ON INDEX public.uk_md_carrier_carrier_name_active IS
    '有效物流商名称按大小写不敏感语义唯一';
