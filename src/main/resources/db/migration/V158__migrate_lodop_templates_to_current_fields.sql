-- 迁移旧 JSH LODOP 模板占位符到当前系统字段名。
-- 当前打印数据由 PrintScriptService 下发原始新字段，模板不再依赖旧字段名。

UPDATE sys_print_template
SET template_html = replace(template_html, '{{organName}}', '{{customerName}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{saleRemark}}', '{{remark}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{billRemark}}', '{{remark}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{displayName}}', '{{brand}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{categoryName}}', '{{category}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{categoryId}}', '{{category}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{model}}', '{{material}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{standardFull}}', '{{spec}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{standard}}', '{{spec}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{operNumber}}', '{{quantity}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{itemWeight}}', '{{weightTon}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{weight}}', '{{weightTon}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{unitWeight}}', '{{pieceWeightTon}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{allPrice}}', '{{amount}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{beginTimeStr}}', '{{startDate}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{endTimeStr}}', '{{endDate}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{billTimeStr}}', '{{billTime}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{carNo}}', '{{vehiclePlate}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{totalPiece}}', '{{totalQuantity}}')
WHERE deleted_flag = FALSE;

UPDATE sys_print_template
SET template_html = replace(template_html, '{{billNo}}', '{{sourceNo}}')
WHERE deleted_flag = FALSE
  AND bill_type IN ('customer-statement', 'freight-statement');

UPDATE sys_print_template
SET template_html = replace(template_html, '{{freightBillNo}}', '{{outboundNo}}')
WHERE deleted_flag = FALSE
  AND bill_type = 'sales-outbound';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{sendDate}}', '{{outboundDate}}')
WHERE deleted_flag = FALSE
  AND bill_type = 'sales-outbound';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{freightBillNo}}', '{{orderNoLabel}}')
WHERE deleted_flag = FALSE
  AND bill_type = 'sales-order';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{sendDate}}', '{{deliveryDate}}')
WHERE deleted_flag = FALSE
  AND bill_type = 'sales-order';
