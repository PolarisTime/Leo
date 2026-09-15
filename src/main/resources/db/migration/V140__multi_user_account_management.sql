-- 多用户账号管理：放开单人模式遗留的账号约束，支撑管理员建号/停用/软删。
--
-- 背景：
--   * V103 为单人部署增加了 chk_sys_user_active_status（deleted_flag OR status='NORMAL'），
--     会阻止“未删除但已停用”的多用户账号；
--   * 原始 V1 的 sys_user_login_name_key 是包含软删记录的全局唯一约束，
--     与 UserAccountRepository.existsByLoginNameAndDeletedFlagFalse 的“软删后可复用”语义不一致；
--   * V139 已放开单活跃用户唯一索引 uk_sys_user_single_active。
--
-- 本迁移只做约束调整，不改写任何历史迁移，也不新增业务数据。

-- 1) 允许未删除账号处于 DISABLED 状态（多用户停用语义）。
ALTER TABLE public.sys_user
    DROP CONSTRAINT IF EXISTS chk_sys_user_active_status;

-- 2) 登录账号唯一性收敛到“未删除账号”：软删后可复用登录账号，
--    active 账号之间仍保持唯一。
ALTER TABLE public.sys_user
    DROP CONSTRAINT IF EXISTS sys_user_login_name_key;

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_login_name_active
    ON public.sys_user (login_name)
    WHERE deleted_flag = false;

COMMENT ON INDEX public.uk_sys_user_login_name_active IS
    '未删除账号登录名唯一；软删账号释放登录名以支持多用户账号管理';
