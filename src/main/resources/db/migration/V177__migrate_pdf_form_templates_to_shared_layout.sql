UPDATE sys_print_template
SET template_html = REPLACE($json$
{
  "template": "print-forms/yingjie-a4-remark.pdf",
  "fields": {
    "customerName": {"source": "customerName", "left": 106, "top": 68, "width": 264, "height": 20, "fontSize": 11},
    "billNo": {"source": ["outboundNo", "orderNo", "billNo"], "left": 430, "top": 68, "width": 130, "height": 20, "fontSize": 11, "minimumFontSize": 11},
    "projectName": {"source": "projectName", "left": 106, "top": 96, "width": 264, "height": 32, "fontSize": 11, "multiline": true, "vertical": "top"},
    "billDate": {"source": ["deliveryDate", "outboundDate", "orderDate"], "format": "chineseDate", "left": 434, "top": 100, "width": 123, "height": 20, "fontSize": 11},
    "projectAddress": {"source": "projectAddress", "left": 106, "top": 132, "width": 264, "height": 20, "fontSize": 11, "multiline": true},
    "vehiclePlate": {"source": "vehiclePlate", "left": 434, "top": 132, "width": 123, "height": 20, "fontSize": 11}
  },
  "table": {
    "left": 28,
    "top": 176,
    "width": 539,
    "headerHeight": 28,
    "rowHeight": 26,
    "maxRowsPerPage": 16,
    "columns": [
      {"key": "index", "label": "序号", "width": 30, "headerFontSize": 11, "fontSize": 10},
      {"key": "brand", "label": "品牌", "width": 65, "headerFontSize": 11, "fontSize": 10},
      {"key": "category", "label": "品名", "width": 45, "headerFontSize": 11, "fontSize": 10},
      {"key": "material", "label": "材质", "width": 70, "headerFontSize": 11, "fontSize": 10, "normalize": "compactAscii", "font": "latinIfAscii"},
      {"key": "spec", "label": "规格", "width": 62, "headerFontSize": 11, "fontSize": 10},
      {"key": "length", "label": "长度", "width": 45, "headerFontSize": 11, "fontSize": 10},
      {"key": "quantity", "label": "件数", "width": 45, "headerFontSize": 11, "fontSize": 10},
      {"key": "pieceWeightTon", "label": "件重/吨", "width": 82, "headerFontSize": 11, "fontSize": 10},
      {"key": "weightTon", "label": "总重/吨", "width": 95, "headerFontSize": 11, "fontSize": 10}
    ]
  },
  "summary": {
    "height": 26,
    "template": "单据备注：__DOLLAR__{remark}    |    合计件数：__DOLLAR__{totalQuantity}件    |    合计重量：__DOLLAR__{totalWeight}吨",
    "fontSize": 10.5,
    "border": true
  },
  "clauses": {
    "height": 96,
    "fontSize": 10.5,
    "lineHeight": 1.28,
    "lines": [
      "1.货物规格、材质、数量及价格在收货时当即点清，并签字生效。",
      "2.对货物必须先行检测合格后使用，如有质量问题需方需在五日内提出书面异议，逾期视为认可，供方负责调换或协助向厂方索赔，否则供方不予处理。需方不得以质量异议为由拒付或少付货款，否则视需方违约且需方向供方支付日息万分之五付违约金。",
      "3.需方收货后，应当即时或合同约定时间全部付款，否则需按日息万分之五支付违约金，同时承担供方实现债权支出的一切费用。"
    ]
  }
}$json$, '__DOLLAR__', '$'),
    asset_ref = COALESCE(NULLIF(asset_ref, ''), 'print-forms/yingjie-a4-remark.pdf'),
    engine = 'PDF_FORM'
WHERE template_type = 'PDF_FORM'
  AND template_html LIKE '%YINGJIE_A4_REMARK%';

UPDATE sys_print_template
SET template_type = 'COORD',
    engine = 'LODOP',
    asset_ref = NULL,
    status = 'DISABLED',
    template_html = 'LODOP.PRINT_INIT("已停用的历史 HTML 模板");'
WHERE template_type = 'HTML'
   OR engine = 'BROWSER_HTML';

ALTER TABLE sys_print_template
    DROP CONSTRAINT IF EXISTS chk_print_template_type,
    DROP CONSTRAINT IF EXISTS chk_print_template_engine,
    DROP CONSTRAINT IF EXISTS chk_print_template_type_engine;

ALTER TABLE sys_print_template
    ADD CONSTRAINT chk_print_template_type
        CHECK (template_type IN ('COORD', 'PDF_FORM')),
    ADD CONSTRAINT chk_print_template_engine
        CHECK (engine IN ('LODOP', 'PDF_FORM')),
    ADD CONSTRAINT chk_print_template_type_engine
        CHECK (
            (template_type = 'COORD' AND engine = 'LODOP')
            OR (template_type = 'PDF_FORM' AND engine = 'PDF_FORM')
        );
