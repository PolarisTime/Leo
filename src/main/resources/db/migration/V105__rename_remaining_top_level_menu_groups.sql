UPDATE sys_menu
SET menu_name = CASE menu_code
  WHEN 'reports' THEN '报表'
  WHEN 'statements' THEN '对账'
  WHEN 'system' THEN '设置'
  ELSE menu_name
END
WHERE menu_code IN ('reports', 'statements', 'system');
