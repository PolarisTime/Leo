-- 同一附件只能绑定一个有效业务记录；目标事务锁和附件行锁负责串行化，唯一索引提供最终保护。

-- 提前阻断绑定写入，使重复检查与唯一索引创建共享同一稳定快照并保留明确错误信息。
LOCK TABLE public.sys_attachment_binding IN SHARE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM public.sys_attachment_binding
         WHERE deleted_flag = FALSE
         GROUP BY attachment_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V108: 存在跨业务记录重复绑定的有效附件，拒绝建立唯一约束';
    END IF;
END $$;

CREATE UNIQUE INDEX uk_sys_attachment_binding_active_attachment
    ON public.sys_attachment_binding (attachment_id)
    WHERE deleted_flag = FALSE;

COMMENT ON INDEX public.uk_sys_attachment_binding_active_attachment IS
    '同一附件最多绑定一个有效业务记录，防止并发请求绕过应用层跨记录复用校验';
