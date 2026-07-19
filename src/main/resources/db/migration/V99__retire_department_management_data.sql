UPDATE public.sys_user
SET department_id = NULL,
    department_name = NULL
WHERE department_id IS NOT NULL
   OR department_name IS NOT NULL;

DELETE FROM public.sys_menu
WHERE menu_code = 'department';
