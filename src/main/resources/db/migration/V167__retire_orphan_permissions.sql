-- 权限目录: 退役无端点/无强制使用的僵尸权限码。
-- 说明:
--   1) 这些权限码在 PermissionCodes 中已删除(不再出现在 sys_permission 同步集合 all());
--      若不清理, 权限矩阵仍会展示可授予但实际无任何端点/门禁效力的"僵尸权限", 误导授权;
--   2) 覆盖三类: (a) 资源无任何写/打印/导出端点(如 finance:*/system:*/steel-quotes:*/inventory:* 写码);
--      (b) 各业务资源的 :print/:export 已统一收敛到 print-exports:print / 模块专属导出端点;
--      (c) :audit 中实际以 update 口径承载的 customer-statements:audit;
--   3) 先删角色绑定再删目录项, 避免历史角色残留指向已删码; 幂等, 重复执行无副作用。

DELETE FROM public.sys_role_permission
WHERE permission_code IN (
    'attachments:delete', 'attachments:preview',
    'customer-statements:audit', 'customer-statements:export', 'customer-statements:print',
    'finance:complete', 'finance:create', 'finance:delete', 'finance:rebuild', 'finance:update',
    'freight-bills:print',
    'import-batches:read', 'material-imports:read',
    'inventory:create', 'inventory:delete', 'inventory:rebuild', 'inventory:update',
    'payments:print',
    'purchase-inbounds:export', 'purchase-inbounds:print',
    'purchase-orders:export', 'purchase-orders:print',
    'quote-sheets:export', 'quote-sheets:print',
    'receipts:print',
    'sales-orders:print',
    'sales-outbounds:export', 'sales-outbounds:print',
    'sales-returns:export', 'sales-returns:print',
    'steel-quotes:create', 'steel-quotes:delete', 'steel-quotes:export', 'steel-quotes:print', 'steel-quotes:update',
    'system:create', 'system:delete', 'system:read', 'system:rebuild'
);

DELETE FROM public.sys_permission
WHERE code IN (
    'attachments:delete', 'attachments:preview',
    'customer-statements:audit', 'customer-statements:export', 'customer-statements:print',
    'finance:complete', 'finance:create', 'finance:delete', 'finance:rebuild', 'finance:update',
    'freight-bills:print',
    'import-batches:read', 'material-imports:read',
    'inventory:create', 'inventory:delete', 'inventory:rebuild', 'inventory:update',
    'payments:print',
    'purchase-inbounds:export', 'purchase-inbounds:print',
    'purchase-orders:export', 'purchase-orders:print',
    'quote-sheets:export', 'quote-sheets:print',
    'receipts:print',
    'sales-orders:print',
    'sales-outbounds:export', 'sales-outbounds:print',
    'sales-returns:export', 'sales-returns:print',
    'steel-quotes:create', 'steel-quotes:delete', 'steel-quotes:export', 'steel-quotes:print', 'steel-quotes:update',
    'system:create', 'system:delete', 'system:read', 'system:rebuild'
);
