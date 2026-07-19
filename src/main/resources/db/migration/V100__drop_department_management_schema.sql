ALTER TABLE public.sys_user
    DROP CONSTRAINT IF EXISTS fk_sys_user_department;

DROP INDEX IF EXISTS public.idx_sys_user_department_id;

ALTER TABLE public.sys_user
    DROP COLUMN IF EXISTS department_id,
    DROP COLUMN IF EXISTS department_name;

DROP TABLE IF EXISTS public.sys_department;
