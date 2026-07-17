DELETE FROM public.sys_print_template
WHERE bill_type IN ('inventory-report', 'io-report');
