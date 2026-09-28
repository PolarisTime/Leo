-- 供应商价格表入口授权(RBAC0)。
--
-- 背景与现状:
--   * V106 已 DROP 后端静态菜单表 sys_menu, 导航改由前端静态页面注册表
--     (aries/src/config/page-registry-*.ts)维护, 后端不再保存菜单元数据;
--   * V139 重建 RBAC0: sys_role / sys_permission / sys_role_permission / sys_user_role;
--   * 菜单可见性由前端静态注册表提供(菜单 code 固定 supplier-price-lists, 路由 /master-data/supplier-price-lists),
--     后端只负责资源级授权。权限目录由 PermissionCatalogSync 启动时按 PermissionCodes.all() 幂等同步,
--     本迁移只做幂等的兜底登记与角色绑定补齐。
--
-- 幂等: 全部使用 ON CONFLICT DO NOTHING / NOT EXISTS, 可重复执行。

-- 1) 兜底登记权限目录: 确保角色绑定引用的权限码一定存在于 sys_permission。
INSERT INTO public.sys_permission (code, resource, action, field, description)
VALUES
    ('supplier-price-lists:read', 'supplier-price-lists', 'read', NULL, '供应商价格表查询'),
    ('supplier-price-lists:create', 'supplier-price-lists', 'create', NULL, '供应商价格表创建'),
    ('supplier-price-lists:update', 'supplier-price-lists', 'update', NULL, '供应商价格表编辑'),
    ('supplier-price-lists:delete', 'supplier-price-lists', 'delete', NULL, '供应商价格表删除')
ON CONFLICT (code) DO NOTHING;

-- 2) 入口读权限补齐: 任何已持有供应商价格表写权限的角色自动补齐 read,
--    避免出现"可写不可见"的入口缺口; 已拥有 read、supplier-price-lists:* 或全局 * 的角色不重复绑定。
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
    'supplier-price-lists:read',
    0,
    'flyway',
    CURRENT_TIMESTAMP,
    FALSE
FROM public.sys_role role
CROSS JOIN max_id
WHERE role.deleted_flag = FALSE
  AND EXISTS (
      SELECT 1
      FROM public.sys_role_permission writer
      WHERE writer.role_id = role.id
        AND writer.deleted_flag = FALSE
        AND writer.permission_code IN (
            'supplier-price-lists:create',
            'supplier-price-lists:update',
            'supplier-price-lists:delete'
        )
  )
  AND NOT EXISTS (
      SELECT 1
      FROM public.sys_role_permission reader
      WHERE reader.role_id = role.id
        AND reader.deleted_flag = FALSE
        AND reader.permission_code IN (
            'supplier-price-lists:read',
            'supplier-price-lists:*',
            '*'
        )
  )
ON CONFLICT (role_id, permission_code) DO NOTHING;

-- 3) 内置超级管理员角色已通过通配权限 '*' 覆盖全部资源, 无需也无法再追加显式绑定, 故跳过。
