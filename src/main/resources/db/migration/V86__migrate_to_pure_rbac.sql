CREATE SEQUENCE public.casbin_sequence AS integer START WITH 1 INCREMENT BY 1;

CREATE TABLE public.casbin_rule (
    id integer PRIMARY KEY DEFAULT nextval('public.casbin_sequence'::regclass),
    ptype character varying(100) NOT NULL,
    v0 character varying(100),
    v1 character varying(100),
    v2 character varying(100),
    v3 character varying(100),
    v4 character varying(100),
    v5 character varying(100),
    CONSTRAINT chk_casbin_rule_ptype CHECK (ptype IN ('p', 'g'))
);

ALTER SEQUENCE public.casbin_sequence OWNED BY public.casbin_rule.id;

CREATE UNIQUE INDEX uk_casbin_rule_policy
    ON public.casbin_rule (
        ptype,
        COALESCE(v0, ''),
        COALESCE(v1, ''),
        COALESCE(v2, ''),
        COALESCE(v3, ''),
        COALESCE(v4, ''),
        COALESCE(v5, '')
    );

COMMENT ON TABLE public.casbin_rule IS 'jCasbin pure RBAC policy rules';
COMMENT ON COLUMN public.casbin_rule.v0 IS 'p: role code; g: subject or child role code';
COMMENT ON COLUMN public.casbin_rule.v1 IS 'p: resource object; g: role or parent role code';
COMMENT ON COLUMN public.casbin_rule.v2 IS 'p: action; g: unused';

LOCK TABLE public.sys_role_permission IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE public.sys_user_role IN SHARE ROW EXCLUSIVE MODE;

INSERT INTO public.casbin_rule (ptype, v0, v1, v2)
SELECT DISTINCT
    'p',
    role.role_code,
    permission.resource_code,
    permission.action_code
FROM public.sys_role_permission permission
JOIN public.sys_role role ON role.id = permission.role_id
WHERE permission.deleted_flag = false
  AND role.deleted_flag = false
  AND role.status = '正常'
  AND permission.resource_code NOT IN (
      'api-key',
      'session',
      'security-key',
      'rate-limit'
  );

INSERT INTO public.casbin_rule (ptype, v0, v1, v2)
SELECT 'p', role.role_code, 'document', 'view_deleted'
FROM public.sys_role role
WHERE role.role_code = 'ADMIN'
  AND role.status = '正常'
  AND role.deleted_flag = false
ON CONFLICT DO NOTHING;

INSERT INTO public.casbin_rule (ptype, v0, v1, v2)
SELECT 'p', role.role_code, permission.resource, permission.action
FROM public.sys_role role
CROSS JOIN (VALUES
    ('mcp_service', 'read'),
    ('mcp_service', 'write')
) AS permission(resource, action)
WHERE role.role_code = 'ADMIN'
  AND role.status = '正常'
  AND role.deleted_flag = false
ON CONFLICT DO NOTHING;

INSERT INTO public.casbin_rule (ptype, v0, v1)
SELECT DISTINCT
    'g',
    user_role.user_id::text,
    role.role_code
FROM public.sys_user_role user_role
JOIN public.sys_user account ON account.id = user_role.user_id
JOIN public.sys_role role ON role.id = user_role.role_id
WHERE user_role.deleted_flag = false
  AND account.deleted_flag = false
  AND account.status = 'NORMAL'
  AND role.deleted_flag = false
  AND role.status = '正常';

-- A child role inherits every permission assigned to its active parent role.
INSERT INTO public.casbin_rule (ptype, v0, v1)
SELECT DISTINCT
    'g',
    child.role_code,
    parent.role_code
FROM public.sys_role child
JOIN public.sys_role parent ON parent.id = child.parent_id
WHERE child.deleted_flag = false
  AND child.status = '正常'
  AND parent.deleted_flag = false
  AND parent.status = '正常';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.casbin_rule
        WHERE v0 IS NULL
           OR btrim(v0) = ''
           OR v1 IS NULL
           OR btrim(v1) = ''
           OR (ptype = 'p' AND (v2 IS NULL OR btrim(v2) = ''))
    ) THEN
        RAISE EXCEPTION 'casbin_rule contains an incomplete RBAC policy';
    END IF;
END
$$;
