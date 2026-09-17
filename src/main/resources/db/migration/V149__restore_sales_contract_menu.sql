-- 销售合同入口授权恢复（RBAC0）。
--
-- 背景与现状：
--   * V71 退役了旧 sales-contract 菜单/动作/角色绑定（当时写的是 sys_menu、
--     sys_menu_action、旧 sys_role_permission）；
--   * V106 已 DROP 后端静态菜单表 sys_menu，导航改由前端静态页面注册表
--     （aries/src/config/page-registry-*.ts）维护，后端不再保存菜单元数据；
--   * V139 重建 RBAC0：sys_role / sys_permission / sys_role_permission / sys_user_role；
--   * V147 只登记了 sales-contracts 权限目录，未做任何角色绑定。
--
-- 因此本迁移不能再插入菜单行（当前架构已无菜单表），而是按 RBAC0 落实等价语义：
-- “角色可访问销售合同入口” == 角色被授予 sales-contracts:* 权限码；菜单可见性
-- 由前端静态注册表提供，后端只负责资源级授权。权限目录由 PermissionCatalogSync 在
-- 启动时按 PermissionCodes.all() 幂等同步，本迁移只做幂等的绑定补齐与兜底登记。
--
-- 幂等：全部使用 ON CONFLICT DO NOTHING / NOT EXISTS，可重复执行。

-- 1) 兜底登记权限目录：确保角色绑定引用的权限码一定存在于 sys_permission。
INSERT INTO public.sys_permission (code, resource, action, field, description)
VALUES
    ('sales-contracts:read', 'sales-contracts', 'read', NULL, '销售合同查询'),
    ('sales-contracts:create', 'sales-contracts', 'create', NULL, '销售合同创建'),
    ('sales-contracts:update', 'sales-contracts', 'update', NULL, '销售合同编辑'),
    ('sales-contracts:delete', 'sales-contracts', 'delete', NULL, '销售合同删除')
ON CONFLICT (code) DO NOTHING;

-- 2) 入口读权限补齐：RBAC0 中“角色-菜单绑定”由 sys_role_permission 承载，
--    sales-contracts:read 是进入销售合同页面/接口的前置。任何仍持有销售合同写权限
--    的角色自动补齐 read，避免出现“可写不可见”的入口缺口。
--    已拥有 read、sales-contracts:* 或全局 * 的角色不重复绑定。
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
    'sales-contracts:read',
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
            'sales-contracts:create',
            'sales-contracts:update',
            'sales-contracts:delete'
        )
  )
  AND NOT EXISTS (
      SELECT 1
      FROM public.sys_role_permission reader
      WHERE reader.role_id = role.id
        AND reader.deleted_flag = FALSE
        AND reader.permission_code IN (
            'sales-contracts:read',
            'sales-contracts:*',
            '*'
        )
  )
ON CONFLICT (role_id, permission_code) DO NOTHING;

-- 3) 超级管理员：内置角色 SUPER_ADMIN 已通过通配权限 '*' 覆盖全部资源，
--    无需也无法再追加显式 sales-contracts 绑定，故跳过显式绑定（仅在此说明）。
--    若某环境缺失内置角色的 * 绑定，属于配置异常，由角色服务与
--    PermissionCatalogSync 在启动时纠正，本迁移不擅自改写内置角色权限。
