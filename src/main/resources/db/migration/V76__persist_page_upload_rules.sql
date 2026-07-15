WITH module_catalog(ordinal, module_key, module_name) AS (
    VALUES
        (1, 'material', '商品资料'),
        (2, 'material-category', '商品类别'),
        (3, 'supplier', '供应商'),
        (4, 'customer', '客户'),
        (5, 'project', '项目'),
        (6, 'carrier', '物流商'),
        (7, 'warehouse', '仓库'),
        (8, 'department', '部门'),
        (9, 'purchase-order', '采购订单'),
        (10, 'purchase-inbound', '采购入库'),
        (11, 'sales-order', '销售订单'),
        (12, 'sales-outbound', '销售出库'),
        (13, 'freight-bill', '物流单'),
        (14, 'customer-statement', '客户对账单'),
        (15, 'freight-statement', '物流对账单'),
        (16, 'receipt', '收款单'),
        (17, 'payment', '付款单'),
        (18, 'ledger-adjustment', '台账调整单'),
        (19, 'cash-ledger', '资金流水'),
        (20, 'general-setting', '通用设置'),
        (21, 'company-setting', '结算主体管理'),
        (22, 'permission', '权限管理'),
        (23, 'user-account', '用户账户'),
        (24, 'role-setting', '角色权限配置'),
        (25, 'role-action-editor', '角色权限配置'),
        (26, 'print-template', '打印模板'),
        (27, 'operation-log', '操作日志'),
        (28, 'session', '会话管理'),
        (29, 'api-key', 'API Key 管理'),
        (30, 'security-key', '安全密钥管理'),
        (31, 'database', '数据库管理'),
        (32, 'io-report', '出入库报表'),
        (33, 'inventory-report', '库存报表')
),
prepared_rules AS (
    SELECT
        335767456976990208::bigint + ordinal AS id,
        module_key,
        'PAGE_UPLOAD_' || REGEXP_REPLACE(UPPER(module_key), '[^A-Z0-9]+', '_', 'g') AS rule_code,
        module_name || '上传命名规则' AS rule_name,
        COALESCE(
            (
                SELECT legacy.rename_pattern
                FROM public.sys_upload_rule legacy
                WHERE legacy.rule_code = 'PAGE_UPLOAD'
                  AND legacy.deleted_flag = FALSE
                ORDER BY legacy.id
                LIMIT 1
            ),
            '{年月日时分秒}_{random8}'
        ) AS rename_pattern,
        '正常' AS status,
        '适用于' || module_name || '页面选择文件和剪贴板粘贴上传' AS remark
    FROM module_catalog
)
INSERT INTO public.sys_upload_rule (
    id,
    module_key,
    rule_code,
    rule_name,
    rename_pattern,
    status,
    remark,
    created_by,
    created_name,
    created_at,
    deleted_flag
)
SELECT
    id,
    module_key,
    rule_code,
    rule_name,
    rename_pattern,
    status,
    remark,
    0,
    'flyway',
    CURRENT_TIMESTAMP,
    FALSE
FROM prepared_rules
WHERE NOT EXISTS (
    SELECT 1
    FROM public.sys_upload_rule existing
    WHERE existing.module_key = prepared_rules.module_key
      AND existing.deleted_flag = FALSE
)
ON CONFLICT (rule_code) DO UPDATE
SET module_key = EXCLUDED.module_key,
    deleted_flag = FALSE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP;
