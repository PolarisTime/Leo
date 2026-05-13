-- Keep menu keys and front-end routes singular.

UPDATE sys_menu SET menu_code = 'material-category'
WHERE menu_code = 'material-categories';

UPDATE sys_menu_action SET menu_code = 'material-category'
WHERE menu_code = 'material-categories';

UPDATE sys_role_action SET menu_code = 'material-category'
WHERE menu_code = 'material-categories';

UPDATE sys_menu SET route_path = '/material-category'
WHERE menu_code = 'material-category'
   OR route_path = '/material-categories';

UPDATE sys_menu SET route_path = '/role-setting'
WHERE route_path = '/role-settings';

UPDATE sys_menu SET route_path = '/session'
WHERE menu_code = 'session'
   OR route_path = '/auth/refresh-tokens';

UPDATE sys_menu SET route_path = '/api-key'
WHERE menu_code = 'api-key'
   OR route_path = '/auth/api-keys';

UPDATE sys_upload_rule
SET module_key = 'material-category',
    rule_code = 'PAGE_UPLOAD_MATERIAL_CATEGORY'
WHERE module_key = 'material-categories';

UPDATE sys_upload_rule
SET module_key = 'role-setting',
    rule_code = 'PAGE_UPLOAD_ROLE_SETTING'
WHERE module_key = 'role-settings';

UPDATE sys_attachment_binding
SET module_key = 'material-category'
WHERE module_key = 'material-categories';

UPDATE sys_attachment_binding
SET module_key = 'role-setting'
WHERE module_key = 'role-settings';

UPDATE sys_operation_log
SET module_key = 'material-category'
WHERE module_key = 'material-categories';

UPDATE sys_operation_log
SET module_key = 'role-setting'
WHERE module_key = 'role-settings';
