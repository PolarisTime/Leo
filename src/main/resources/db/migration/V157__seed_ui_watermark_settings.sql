-- Seed UI watermark settings shown on the General Settings page.

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'UI_WATERMARK_ENABLED',
    '页面背景水印',
    '界面显示',
    'UI',
    'yyyy',
    1,
    'YEARLY',
    'OFF',
    '禁用',
    '启用后，在系统页面背景显示平铺水印'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'UI_WATERMARK_ENABLED' AND deleted_flag = FALSE
);

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'SYS_WATERMARK_CONTENT',
    '页面水印内容',
    '界面显示',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    '{username}  {time}',
    '正常',
    '支持变量 {username}、{time}、{date}'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'SYS_WATERMARK_CONTENT' AND deleted_flag = FALSE
);

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'SYS_WATERMARK_FONT_SIZE',
    '页面水印字号',
    '界面显示',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    '18',
    '正常',
    '页面背景水印字号，单位 px，建议范围 8 到 72'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'SYS_WATERMARK_FONT_SIZE' AND deleted_flag = FALSE
);

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'SYS_WATERMARK_ROTATE',
    '页面水印角度',
    '界面显示',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    '-22',
    '正常',
    '页面背景水印旋转角度，范围 -90 到 90'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'SYS_WATERMARK_ROTATE' AND deleted_flag = FALSE
);

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'SYS_WATERMARK_COLOR',
    '页面水印颜色',
    '界面显示',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    'rgba(0,0,0,0.08)',
    '正常',
    '页面背景水印颜色，支持 rgba 或十六进制颜色'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'SYS_WATERMARK_COLOR' AND deleted_flag = FALSE
);

INSERT INTO sys_no_rule (id, setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, status, remark)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    'SYS_WATERMARK_DENSITY',
    '页面水印密度',
    '界面显示',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    '200',
    '正常',
    '页面背景水印间距，单位 px，数值越小水印越密，建议范围 50 到 400'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_no_rule WHERE setting_code = 'SYS_WATERMARK_DENSITY' AND deleted_flag = FALSE
);
