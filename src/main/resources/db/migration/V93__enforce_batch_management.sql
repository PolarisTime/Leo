DELETE FROM public.sys_general_setting
WHERE setting_code = 'SYS_FORCE_BATCH_MANAGEMENT';

ALTER TABLE public.md_material
    DROP COLUMN batch_no_enabled;
