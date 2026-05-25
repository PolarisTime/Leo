-- Align auth_api_key.status with ApiKeyStatus persisted by @Enumerated(EnumType.STRING).
UPDATE auth_api_key
SET status = CASE status
    WHEN '有效' THEN 'NORMAL'
    WHEN '禁用' THEN 'DISABLED'
    ELSE status
END
WHERE status IN ('有效', '禁用');
