-- 统一历史操作日志模块名称，确保筛选值与当前领域术语一致。
UPDATE public.sys_operation_log
SET module_name = CASE
        WHEN module_name IN ('Auth', '认证授权') THEN '身份认证'
        WHEN module_name = '公司信息' THEN '结算主体'
        ELSE module_name
    END
WHERE module_name IN ('Auth', '认证授权', '公司信息');

UPDATE public.sys_operation_log_unpartitioned
SET module_name = CASE
        WHEN module_name IN ('Auth', '认证授权') THEN '身份认证'
        WHEN module_name = '公司信息' THEN '结算主体'
        ELSE module_name
    END
WHERE module_name IN ('Auth', '认证授权', '公司信息');
