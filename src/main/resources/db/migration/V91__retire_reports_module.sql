DELETE FROM public.casbin_rule
WHERE ptype = 'p'
  AND v1 IN ('inventory-report', 'io-report');

DELETE FROM public.sys_menu_action
WHERE menu_code IN ('inventory-report', 'io-report');

DELETE FROM public.sys_menu
WHERE menu_code IN ('inventory-report', 'io-report');

DELETE FROM public.sys_menu
WHERE menu_code = 'reports';
