WITH rule_seed(setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, remark, sort_order) AS (
    VALUES
        ('RULE_MAT', '商品编码规则', '商品资料', 'MAT{seq}', 'NONE', 4, 'NEVER', 'MAT0001', '商品资料系统自动编号', 10),
        ('RULE_MC', '商品类别编码规则', '商品类别', 'MC{seq}', 'NONE', 4, 'NEVER', 'MC0001', '商品类别系统自动编号', 20),
        ('RULE_SUP', '供应商编码规则', '供应商资料', 'SUP{seq}', 'NONE', 4, 'NEVER', 'SUP0001', '供应商资料系统自动编号', 30),
        ('RULE_CUST', '客户编码规则', '客户资料', 'CUS{seq}', 'NONE', 4, 'NEVER', 'CUS0001', '客户资料系统自动编号', 40),
        ('RULE_CAR', '物流方编码规则', '物流方资料', 'CAR{seq}', 'NONE', 4, 'NEVER', 'CAR0001', '物流方资料系统自动编号', 50),
        ('RULE_WH', '仓库编码规则', '仓库资料', 'WH{seq}', 'NONE', 4, 'NEVER', 'WH0001', '仓库资料系统自动编号', 60),
        ('RULE_PO', '采购订单编号规则', '采购订单', 'PO{yyyy}{seq}', 'yyyy', 6, 'YEARLY', 'PO2026000001', '采购订单系统自动编号', 70),
        ('RULE_PI', '采购入库编号规则', '采购入库', 'PI{yyyy}{seq}', 'yyyy', 6, 'YEARLY', 'PI2026000001', '采购入库系统自动编号', 80),
        ('RULE_SO', '销售订单编号规则', '销售订单', 'SO{yyyy}{seq}', 'yyyy', 6, 'YEARLY', 'SO2026000001', '销售订单系统自动编号', 90),
        ('RULE_OB', '销售出库编号规则', '销售出库', 'OB{yyyy}{seq}', 'yyyy', 6, 'YEARLY', 'OB2026000001', '销售出库系统自动编号', 100),
        ('RULE_FB', '物流单编号规则', '物流单', 'FB{yyyy}{seq}', 'yyyy', 6, 'YEARLY', 'FB2026000001', '物流单系统自动编号', 110),
        ('RULE_SS', '供应商对账单编号规则', '供应商对账单', 'SS{yyyy}{seq}', 'yyyy', 6, 'YEARLY', 'SS2026000001', '供应商对账单系统自动编号', 120),
        ('RULE_CS', '客户对账单编号规则', '客户对账单', 'CS{yyyy}{seq}', 'yyyy', 6, 'YEARLY', 'CS2026000001', '客户对账单系统自动编号', 130),
        ('RULE_FS', '物流对账单编号规则', '物流对账单', 'FS{yyyy}{seq}', 'yyyy', 6, 'YEARLY', 'FS2026000001', '物流对账单系统自动编号', 140)
),
missing_rules AS (
    SELECT rule_seed.*
    FROM rule_seed
    WHERE NOT EXISTS (
        SELECT 1
        FROM sys_no_rule existing
        WHERE existing.setting_code = rule_seed.setting_code
          AND existing.deleted_flag = FALSE
    )
),
numbered_rules AS (
    SELECT
        COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000)
            + ROW_NUMBER() OVER (ORDER BY sort_order) AS id,
        setting_code,
        setting_name,
        bill_name,
        prefix,
        date_rule,
        serial_length,
        reset_rule,
        sample_no,
        remark
    FROM missing_rules
)
INSERT INTO sys_no_rule (
    id,
    setting_code,
    setting_name,
    bill_name,
    prefix,
    date_rule,
    serial_length,
    reset_rule,
    sample_no,
    status,
    remark
)
SELECT
    id,
    setting_code,
    setting_name,
    bill_name,
    prefix,
    date_rule,
    serial_length,
    reset_rule,
    sample_no,
    '正常',
    remark
FROM numbered_rules
ON CONFLICT (setting_code) DO UPDATE
SET setting_name = EXCLUDED.setting_name,
    bill_name = EXCLUDED.bill_name,
    prefix = EXCLUDED.prefix,
    date_rule = EXCLUDED.date_rule,
    serial_length = EXCLUDED.serial_length,
    reset_rule = EXCLUDED.reset_rule,
    sample_no = EXCLUDED.sample_no,
    current_period = NULL,
    next_serial_value = NULL,
    status = EXCLUDED.status,
    remark = EXCLUDED.remark,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP,
    deleted_flag = FALSE
WHERE sys_no_rule.deleted_flag = TRUE;
