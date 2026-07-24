-- 单账号是否存在是初始化状态的唯一来源，旧 OOBE 完成开关不再参与判断。
DELETE FROM public.sys_general_setting
 WHERE setting_code = 'SYS_OOBE_COMPLETED';
