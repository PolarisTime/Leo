INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type)
VALUES (2007, 'material-categories', '商品类别', 'master', '/material-categories', 'TagsOutlined', 7, '菜单');

INSERT INTO sys_menu_action (id, menu_code, action_code, action_name) VALUES
(200701, 'material-categories', 'read', '查看'),
(200702, 'material-categories', 'create', '新增'),
(200703, 'material-categories', 'update', '编辑'),
(200704, 'material-categories', 'delete', '删除');
