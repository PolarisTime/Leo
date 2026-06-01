UPDATE sys_no_rule
SET remark = '开启后所有出入库明细均按批号管理处理，并覆盖商品资料中的批号启用开关；未填写批号时按批号自动生成开关决定是否补齐，默认关闭'
WHERE setting_code = 'SYS_FORCE_BATCH_MANAGEMENT'
  AND deleted_flag = FALSE;
