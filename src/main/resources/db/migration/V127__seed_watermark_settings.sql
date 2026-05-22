-- Seed watermark system settings (UI_WATERMARK_ENABLED + SYS_WATERMARK_CONTENT)
-- Both default to disabled/empty so watermark is off until admin enables it in 通用设置.

INSERT INTO system_settings (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark, created_at, updated_at)
SELECT
  REPLACE(gen_random_uuid()::text, '-', ''),
  'UI_WATERMARK_ENABLED',
  '全局水印开关',
  '水印',
  'SYS',
  'NONE',
  6,
  'NEVER',
  'ON',
  '禁用',
  '开启后页面显示全局水印，内容为空时默认显示登录用户名',
  NOW(),
  NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_code = 'UI_WATERMARK_ENABLED');

INSERT INTO system_settings (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark, created_at, updated_at)
SELECT
  REPLACE(gen_random_uuid()::text, '-', ''),
  'SYS_WATERMARK_CONTENT',
  '水印内容',
  '水印',
  'SYS',
  'NONE',
  6,
  'NEVER',
  '',
  '正常',
  '留空默认显示登录用户名',
  NOW(),
  NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_code = 'SYS_WATERMARK_CONTENT');
