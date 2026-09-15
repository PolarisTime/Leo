-- RBAC0：用户-角色-权限落库（多用户最小可用模型）。
--
-- 历史状态：
--   * V87 已删除旧的 sys_role_permission / sys_user_role（配合 jCasbin 迁移）；
--   * V94 已删除旧的 sys_role / sys_role_conflict / casbin_rule；
--   * V103 建立了单活跃用户唯一索引 uk_sys_user_single_active。
-- 本迁移在此基础上按 RBAC0 重新建立最小模型，不改写任何历史迁移。
--
-- 说明：
--   * RBAC0 需要多用户，因此放开单活跃用户约束（仅放开，不创建用户）；
--   * 种子仅创建内置角色 SUPER_ADMIN 并挂载通配权限 '*'，再分配给现有未删除用户；
--   * 权限目录由应用启动时的 PermissionCatalogSyncService 按 PermissionCodes.all()
--     幂等 upsert 到 sys_permission，保持 PermissionCodes 为单一来源。

-- 1) 放开单活跃用户约束：RBAC0 需多用户；仅放开约束，不创建用户。
DROP INDEX IF EXISTS public.uk_sys_user_single_active;

-- 2) 清理历史残留（历史链条已删除，此处在任何环境均保持幂等）。
DROP TABLE IF EXISTS public.sys_role_permission;
DROP TABLE IF EXISTS public.sys_user_role;
DROP TABLE IF EXISTS public.sys_role;

-- 3) 角色表。
CREATE TABLE public.sys_role (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    description character varying(255),
    builtin boolean DEFAULT false NOT NULL,
    status character varying(16) DEFAULT '正常'::character varying NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT sys_role_pkey PRIMARY KEY (id),
    CONSTRAINT chk_sys_role_status CHECK (((status)::text = ANY ((ARRAY['正常'::character varying, '禁用'::character varying])::text[])))
);

-- 软删除场景下仅约束未删除角色编码唯一，允许历史角色编码复用。
CREATE UNIQUE INDEX uk_sys_role_code ON public.sys_role USING btree (code) WHERE (deleted_flag = false);
CREATE INDEX idx_sys_role_status ON public.sys_role USING btree (status);

COMMENT ON TABLE public.sys_role IS 'RBAC0 角色';
COMMENT ON COLUMN public.sys_role.code IS '角色编码，未删除角色内唯一';
COMMENT ON COLUMN public.sys_role.builtin IS '内置角色标记，内置角色禁止删除或修改编码';
COMMENT ON COLUMN public.sys_role.status IS '角色状态：正常 / 禁用';

-- 4) 权限目录表（由 PermissionCatalogSyncService 幂等同步）。
CREATE TABLE public.sys_permission (
    code character varying(128) NOT NULL,
    resource character varying(64) NOT NULL,
    action character varying(64) NOT NULL,
    field character varying(64),
    description character varying(255),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    CONSTRAINT sys_permission_pkey PRIMARY KEY (code)
);

CREATE INDEX idx_sys_permission_resource ON public.sys_permission USING btree (resource);

COMMENT ON TABLE public.sys_permission IS 'RBAC0 权限目录，可从 PermissionCodes.all() 重建';
COMMENT ON COLUMN public.sys_permission.code IS '权限码，格式 资源:动作[:字段]，通配为 *';
COMMENT ON COLUMN public.sys_permission.resource IS '资源（REST 路径复数 kebab-case）';
COMMENT ON COLUMN public.sys_permission.action IS '动作';
COMMENT ON COLUMN public.sys_permission.field IS '可选字段级标识';

-- 5) 用户-角色关联表（整体替换时物理删除旧关联后重建）。
CREATE TABLE public.sys_user_role (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT sys_user_role_pkey PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_role_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES public.sys_user (id),
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES public.sys_role (id)
);

CREATE INDEX idx_sys_user_role_user_id ON public.sys_user_role USING btree (user_id);
CREATE INDEX idx_sys_user_role_role_id ON public.sys_user_role USING btree (role_id);

COMMENT ON TABLE public.sys_user_role IS 'RBAC0 用户-角色关联';

-- 6) 角色-权限关联表。
--    注意：permission_code 刻意不对 sys_permission(code) 建外键，以允许
--    '资源:*' 资源级通配权限码（合法但不在权限目录中），由服务层校验合法性。
CREATE TABLE public.sys_role_permission (
    id bigint NOT NULL,
    role_id bigint NOT NULL,
    permission_code character varying(128) NOT NULL,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT sys_role_permission_pkey PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_permission_role_code UNIQUE (role_id, permission_code),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES public.sys_role (id)
);

CREATE INDEX idx_sys_role_permission_role_id ON public.sys_role_permission USING btree (role_id);
CREATE INDEX idx_sys_role_permission_permission_code ON public.sys_role_permission USING btree (permission_code);

COMMENT ON TABLE public.sys_role_permission IS 'RBAC0 角色-权限关联';

-- 7) 种子：内置超级管理员角色 + 通配权限 + 现有未删除用户。
INSERT INTO public.sys_role (id, code, name, description, builtin, status)
SELECT 1, 'SUPER_ADMIN', '超级管理员', '内置角色：拥有全部权限', true, '正常'
WHERE NOT EXISTS (
    SELECT 1 FROM public.sys_role WHERE code = 'SUPER_ADMIN'
);

INSERT INTO public.sys_permission (code, resource, action, field, description)
VALUES ('*', '*', '*', NULL, '全部权限通配')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.sys_role_permission (id, role_id, permission_code)
SELECT 1, role.id, '*'
FROM public.sys_role role
WHERE role.code = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM public.sys_role_permission rp
      WHERE rp.role_id = role.id
        AND rp.permission_code = '*'
  );

-- 为现有未删除用户分配内置超级管理员角色；已有关联的用户保持其既有角色不变。
INSERT INTO public.sys_user_role (id, user_id, role_id)
SELECT 1000000 + ROW_NUMBER() OVER (ORDER BY account.id)::bigint, account.id, role.id
FROM public.sys_user account
JOIN public.sys_role role ON role.code = 'SUPER_ADMIN'
WHERE account.deleted_flag = false
  AND NOT EXISTS (
      SELECT 1 FROM public.sys_user_role ur WHERE ur.user_id = account.id
  );
