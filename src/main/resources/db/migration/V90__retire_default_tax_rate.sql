DELETE FROM public.sys_general_setting
WHERE setting_code = 'SYS_DEFAULT_TAX_RATE';

ALTER TABLE public.sys_company_setting
    DROP COLUMN tax_rate;
