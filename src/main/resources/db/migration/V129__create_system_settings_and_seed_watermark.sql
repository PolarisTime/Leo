CREATE TABLE IF NOT EXISTS system_settings (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  setting_code VARCHAR(128) NOT NULL,
  setting_name VARCHAR(255),
  bill_name VARCHAR(100),
  prefix VARCHAR(20) DEFAULT 'SYS',
  date_rule VARCHAR(50) DEFAULT 'NONE',
  serial_length INT DEFAULT 6,
  reset_rule VARCHAR(50) DEFAULT 'NEVER',
  sample_no VARCHAR(500),
  status VARCHAR(20) DEFAULT '正常',
  remark TEXT,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_system_settings_code') THEN
    CREATE UNIQUE INDEX idx_system_settings_code ON system_settings(setting_code);
  END IF;
END $$;

INSERT INTO system_settings (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark, created_at, updated_at)
SELECT replace(gen_random_uuid()::text, '-', ''), 'UI_WATERMARK_ENABLED', '全局水印开关', '水印', 'SYS', 'NONE', 6, 'NEVER', 'ON', '禁用', '开启后页面显示全局水印', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_code = 'UI_WATERMARK_ENABLED');

INSERT INTO system_settings (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark, created_at, updated_at)
SELECT replace(gen_random_uuid()::text, '-', ''), 'SYS_WATERMARK_CONTENT', '水印内容', '水印', 'SYS', 'NONE', 6, 'NEVER', '', '正常', '留空默认显示登录用户名', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_code = 'SYS_WATERMARK_CONTENT');
