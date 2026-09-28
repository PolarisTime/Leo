-- 值映射/别名表(可维护数据): 把类别/材质/定尺/品牌四个维度的"同一含义多种写法"归一规则从硬编码改为表数据。
-- 说明:
--   1) 维度取值固定四类: CATEGORY(类别) / MATERIAL(材质) / LENGTH(定尺) / BRAND(品牌);
--   2) **只做同一含义多写法归一, 不做跨语义合并**: 例如 "直条" 与 "螺纹钢" 是同一类别的两种写法(归一),
--      而 "螺纹钢" 与 "盘螺" 是不同类别, 严禁通过本表互相映射;
--   3) 自映射(source_value = target_value)无意义, 由 CHECK 约束拒绝;
--   4) 同一维度下同一 source_value 只允许一条未删除记录(部分唯一索引), 软删后可重建同 source;
--   5) 种子行 = 原 CategoryNormalizer 的硬编码规则 "直条 → 螺纹钢"(类别维度), 幂等插入;
--      应用侧仍保留该硬编码常量作为"表数据缺失/未执行本迁移"时的兜底(行为不变)。
-- 幂等: 建表使用 IF NOT EXISTS, 种子使用 ON CONFLICT DO NOTHING, 权限登记使用 ON CONFLICT DO NOTHING。

CREATE TABLE IF NOT EXISTS public.md_value_alias (
    id bigint NOT NULL,
    dimension character varying(16) NOT NULL,
    source_value character varying(64) NOT NULL,
    target_value character varying(64) NOT NULL,
    remark character varying(255),
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT md_value_alias_pkey PRIMARY KEY (id),
    CONSTRAINT ck_value_alias_dimension CHECK (dimension::text IN ('CATEGORY', 'MATERIAL', 'LENGTH', 'BRAND')),
    -- 自映射无意义: 源值必须与目标值不同
    CONSTRAINT ck_value_alias_self_mapping CHECK (source_value::text <> target_value::text)
);

-- 同一维度下同一源值仅一条未删除映射; 软删后可重建同 source
CREATE UNIQUE INDEX IF NOT EXISTS uk_value_alias_dimension_source
    ON public.md_value_alias USING btree (dimension, source_value)
    WHERE deleted_flag = false;

-- 归一化查询按维度批量取映射
CREATE INDEX IF NOT EXISTS idx_value_alias_dimension
    ON public.md_value_alias USING btree (dimension);

COMMENT ON TABLE public.md_value_alias IS
    '值映射/别名表: 类别(CATEGORY)/材质(MATERIAL)/定尺(LENGTH)/品牌(BRAND)四个维度的同义写法归一; '
    '只做同一含义多写法归一, 不做跨语义合并(如 螺纹钢 与 盘螺 不得互映)';
COMMENT ON COLUMN public.md_value_alias.dimension IS
    '维度: CATEGORY 类别 / MATERIAL 材质 / LENGTH 定尺 / BRAND 品牌';
COMMENT ON COLUMN public.md_value_alias.source_value IS
    '源写法(用户录入/历史数据里的写法), 同维度内未删除记录唯一';
COMMENT ON COLUMN public.md_value_alias.target_value IS
    '目标写法(归一后的规范写法), 必须与 source_value 不同(自映射无意义)';
COMMENT ON COLUMN public.md_value_alias.remark IS '备注(说明该别名来源, 如"某供应商价格表写法")';
COMMENT ON COLUMN public.md_value_alias.deleted_flag IS '软删除标记, true 表示已删除; 仅 false 的行参与归一与唯一性约束';

-- 种子: 原 CategoryNormalizer 硬编码规则(直条 ≡ 螺纹钢)固化为表数据; 应用侧硬编码常量降级为兜底。
-- 幂等: 重复执行时命中 uk_value_alias_dimension_source → ON CONFLICT DO NOTHING 跳过。
WITH next_id AS (
    SELECT COALESCE(MAX(id), 0) + 1 AS value FROM public.md_value_alias
)
INSERT INTO public.md_value_alias (id, dimension, source_value, target_value, remark, created_by, created_name)
SELECT next_id.value,
       'CATEGORY',
       '直条',
       '螺纹钢',
       '商品信息 md_material 字典值, 与比价单行类别 螺纹钢 为同一类别(原 CategoryNormalizer 硬编码规则)',
       0,
       'flyway'
FROM next_id
ON CONFLICT DO NOTHING;

-- 权限兜底登记(RBAC0): 权限目录由 PermissionCatalogSync 启动时按 PermissionCodes.all() 幂等同步,
-- 本段只做幂等兜底登记 + 默认角色绑定补齐, 与 V169 对 supplier-price-lists 的处理一致。
INSERT INTO public.sys_permission (code, resource, action, field, description)
VALUES
    ('value-aliases:read', 'value-aliases', 'read', NULL, '值映射/别名查询'),
    ('value-aliases:create', 'value-aliases', 'create', NULL, '值映射/别名创建'),
    ('value-aliases:update', 'value-aliases', 'update', NULL, '值映射/别名编辑'),
    ('value-aliases:delete', 'value-aliases', 'delete', NULL, '值映射/别名删除')
ON CONFLICT (code) DO NOTHING;

-- 默认集合: 已经能维护供应商价格表(或已持有值映射写权限)的角色, 自动补齐值映射读权限,
-- 避免出现"能维护价格表却看不到别名规则"的入口缺口; 不默认授予写权限。
WITH max_id AS (
    SELECT COALESCE(MAX(id), 0) AS value FROM public.sys_role_permission
)
INSERT INTO public.sys_role_permission (
    id,
    role_id,
    permission_code,
    created_by,
    created_name,
    created_at,
    deleted_flag
)
SELECT
    max_id.value + ROW_NUMBER() OVER (ORDER BY role.id),
    role.id,
    'value-aliases:read',
    0,
    'flyway',
    CURRENT_TIMESTAMP,
    FALSE
FROM public.sys_role role
CROSS JOIN max_id
WHERE role.deleted_flag = FALSE
  AND EXISTS (
      SELECT 1
      FROM public.sys_role_permission granted
      WHERE granted.role_id = role.id
        AND granted.deleted_flag = FALSE
        AND (
            granted.permission_code LIKE 'supplier-price-lists:%'
            OR granted.permission_code LIKE 'value-aliases:%'
        )
  )
  AND NOT EXISTS (
      SELECT 1
      FROM public.sys_role_permission reader
      WHERE reader.role_id = role.id
        AND reader.deleted_flag = FALSE
        AND reader.permission_code IN ('value-aliases:read', 'value-aliases:*', '*')
  );
