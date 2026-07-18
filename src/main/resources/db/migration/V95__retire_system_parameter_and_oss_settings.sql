DELETE FROM public.sys_menu
WHERE menu_code IN (
    'number-rules',
    'no-rule',
    'general-setting',
    'system-parameters',
    'oss-setting'
);

DELETE FROM public.sys_general_setting
WHERE setting_code IN (
    'UI_DEFAULT_LIST_PAGE_SIZE',
    'UI_HIDE_AUDITED_LIST_RECORDS',
    'SYS_CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER'
);

DROP TABLE public.sys_oss_setting;
