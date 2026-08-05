-- 登记生产缺失的物流对账单 PDF 打印模板（文件托管）
--
-- 背景：生产库从未执行 seed 线，sys_print_template 缺少 DEFAULT_FREIGHT_STATEMENT_PDF_FORM
-- 登记，导致物流对账单在生产没有 PDF 打印模板可选（文件 print-forms/default-freight-statement.layout.json
-- 打包进 JAR 也不会被 PrintTemplateFileSyncRunner 加载——该同步器只处理数据库中已登记的 FILE 记录）。
--
-- 本迁移在生产补齐这条登记：内容由启动时 PrintTemplateFileSyncRunner 从文件同步（source_checksum
-- 为文件真实 SHA-256，保证同步时不产生版本号递增），结算主体为空表示通用模板。
-- 幂等：仅当 freight-statement 尚无有效模板时插入，重复执行无副作用。

INSERT INTO sys_print_template (
    id,
    bill_type,
    template_name,
    template_code,
    template_html,
    is_default,
    created_by,
    created_name,
    created_at,
    updated_by,
    updated_name,
    updated_at,
    deleted_flag,
    template_type,
    engine,
    asset_ref,
    version_no,
    status,
    sync_mode,
    source_ref,
    source_checksum,
    settlement_company_id,
    settlement_company_name
)
SELECT
    700540000000000040,
    'freight-statement',
    '默认物流对账 PDF',
    'DEFAULT_FREIGHT_STATEMENT_PDF_FORM',
    '{"page":{"width":595,"height":842},"fields":{"placeholder":{"source":"placeholder","left":28,"top":28,"width":539,"height":24}},"static":[{"type":"text","text":"文件托管模板待同步","left":28,"top":28,"width":539,"height":24}]}',
    false,
    0,
    'flyway',
    CURRENT_TIMESTAMP,
    0,
    'flyway',
    CURRENT_TIMESTAMP,
    false,
    'PDF_FORM',
    'PDF_FORM',
    NULL,
    1,
    'ACTIVE',
    'FILE',
    'print-forms/default-freight-statement.layout.json',
    '22c68d57ff3f6cb7d728710701102cbf5ef6c24bf9e0f7921aab83b8b9c3d3eb',
    NULL,
    NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_print_template
    WHERE bill_type = 'freight-statement'
      AND deleted_flag = false
);
