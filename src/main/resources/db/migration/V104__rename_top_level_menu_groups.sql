UPDATE sys_menu
SET menu_name = CASE menu_code
  WHEN 'master' THEN '基础数据'
  WHEN 'purchase' THEN '采购'
  WHEN 'sales' THEN '销售'
  WHEN 'freight' THEN '物流'
  WHEN 'contracts' THEN '合同'
  WHEN 'finance' THEN '财务'
  ELSE menu_name
END
WHERE menu_code IN ('master', 'purchase', 'sales', 'freight', 'contracts', 'finance');
