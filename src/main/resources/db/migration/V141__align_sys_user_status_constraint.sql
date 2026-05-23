-- Align sys_user.status with UserStatus persisted by @Enumerated(EnumType.STRING).
UPDATE sys_user
SET status = CASE status
    WHEN '正常' THEN 'NORMAL'
    WHEN '禁用' THEN 'DISABLED'
    WHEN '已删除' THEN 'DISABLED'
    ELSE status
END
WHERE status IN ('正常', '禁用', '已删除');

ALTER TABLE sys_user DROP CONSTRAINT IF EXISTS chk_sys_user_status;
ALTER TABLE sys_user ADD CONSTRAINT chk_sys_user_status
    CHECK (status IN ('NORMAL', 'DISABLED'));
