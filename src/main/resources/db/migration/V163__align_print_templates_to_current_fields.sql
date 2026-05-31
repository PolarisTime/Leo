-- Align coordinate print templates to backend-provided current-system camelCase fields.
-- No legacy frontend aliases or underscored private print fields are kept.

INSERT INTO sys_print_template (id, bill_type, template_name, template_type, template_html, is_default, deleted_flag)
VALUES (
    700540000000000029,
    'sales-order',
    '颖捷A4打印_带备注 PDF',
    'PDF_FORM',
    '{"form":"YINGJIE_A4_REMARK","template":"print-forms/yingjie-a4-remark.pdf"}',
    false,
    false
)
ON CONFLICT (id) DO UPDATE
SET bill_type = EXCLUDED.bill_type,
    template_name = EXCLUDED.template_name,
    template_type = EXCLUDED.template_type,
    template_html = EXCLUDED.template_html,
    deleted_flag = FALSE;

UPDATE sys_print_template target
SET bill_type = 'freight-bill',
    template_name = '物流单A版',
    template_type = source.template_type,
    template_html = source.template_html,
    is_default = false,
    deleted_flag = false
FROM sys_print_template source
WHERE target.id = 700540000000000024
  AND source.id = 700540000000000025
  AND target.template_type = 'PDF_FORM';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_printDate}}', '{{printDate}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_printTime}}', '{{printTime}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_billNoLabel}}', '单据号：{{billNo}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{orderNoLabel}}', '{{orderNo}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{outboundNoLabel}}', '{{outboundNo}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_dateYear}}', '{{dateYear}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_dateMonth}}', '{{dateMonth}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_dateDay}}', '{{dateDay}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_rowTop}}', '{{rowTop}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_sumTop2}}', '{{sumTop2}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_sumTop}}', '{{sumTop}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_emptyRowTop}}', '{{emptyRowTop}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{#if _hasEmptyRows}}', '{{#if hasEmptyRows}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_hasEmptyRows}}', '{{hasEmptyRows}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_index}}', '{{index}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{#if _isSeparator}}', '{{#if isSeparator}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_isSeparator}}', '{{isSeparator}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_groupName}}', '{{groupName}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{#if _needsNewPage}}', '{{#if needsNewPage}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_needsNewPage}}', '{{needsNewPage}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{#if _needsSeparator}}', '{{#if needsSeparator}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_needsSeparator}}', '{{needsSeparator}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_footerTop}}', '{{footerTop}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_footerLineTop}}', '{{footerLineTop}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{_footerDateTop}}', '{{footerDateTop}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{brandDisplay}}', '{{brand}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{pieceWeightDisplay}}', '{{pieceWeightTon}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{weightTonDisplay}}', '{{weightTon}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{unitPriceDisplay}}', '{{unitPrice}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{amountDisplay}}', '{{amount}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_html = replace(template_html, '{{totalWeightDisplay}}', '{{totalWeight}}')
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET template_type = 'COORD',
    template_html = $template$
LODOP.PRINT_INITA(0, 20, 2970, 2100, "A4打印模版（带备注）");
LODOP.SET_PRINT_PAGESIZE(1,2970,2100,"");
LODOP.SET_PRINT_STYLE("FontName","微软雅黑");
LODOP.SET_PRINT_STYLE("FontSize",9);
LODOP.SET_PRINT_STYLE("Italic",0);

LODOP.ADD_PRINT_TEXT(8,10,732.65625,28,"嘉兴颖捷建材有限公司（供货单）");
LODOP.SET_PRINT_STYLEA(0,"FontSize",16);
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);

LODOP.ADD_PRINT_TEXT(8,10,230,20,"单据备注：{{remark}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.SET_PRINT_STYLEA(0,"Alignment",0);

LODOP.ADD_PRINT_RECT(40,10,732.65625,44,0,1);
LODOP.ADD_PRINT_LINE(40,490,84,490,0,1);
LODOP.ADD_PRINT_TEXT(46,18,464,32,"需方公司：{{customerName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.ADD_PRINT_TEXT(46,498,236.65625,32,"销售订单号：{{orderNo}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);

LODOP.ADD_PRINT_RECT(84,10,732.65625,44,0,1);
LODOP.ADD_PRINT_LINE(84,490,128,490,0,1);
LODOP.ADD_PRINT_TEXT(90,18,464,32,"工程名称：{{projectName}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.ADD_PRINT_TEXT(90,498,236.65625,32,"日期：{{deliveryDate}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);

LODOP.ADD_PRINT_RECT(128,10,732.65625,44,0,1);
LODOP.ADD_PRINT_LINE(128,490,172,490,0,1);
LODOP.ADD_PRINT_TEXT(134,18,464,32,"项目地址：{{projectAddress}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);
LODOP.ADD_PRINT_TEXT(134,498,236.65625,32,"车号：{{vehiclePlate}}");
LODOP.SET_PRINT_STYLEA(0,"FontSize",10);

LODOP.ADD_PRINT_RECT(176,10,78,28,0,1);
LODOP.ADD_PRINT_TEXT(183,12,74,16,"品牌");
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.ADD_PRINT_RECT(176,88,47.34375,28,0,1);
LODOP.ADD_PRINT_TEXT(183,90,43.34375,16,"品名");
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.ADD_PRINT_RECT(176,135.34375,78,28,0,1);
LODOP.ADD_PRINT_TEXT(183,137.34375,74,16,"材质");
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.ADD_PRINT_RECT(176,213.34375,72,28,0,1);
LODOP.ADD_PRINT_TEXT(183,215.34375,68,16,"规格");
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.ADD_PRINT_RECT(176,285.34375,60,28,0,1);
LODOP.ADD_PRINT_TEXT(183,287.34375,56,16,"长度");
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.ADD_PRINT_RECT(176,345.34375,64,28,0,1);
LODOP.ADD_PRINT_TEXT(183,347.34375,60,16,"件数");
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.ADD_PRINT_RECT(176,409.34375,66,28,0,1);
LODOP.ADD_PRINT_TEXT(183,411.34375,62,16,"件重/吨");
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.ADD_PRINT_RECT(176,475.34375,57,28,0,1);
LODOP.ADD_PRINT_TEXT(183,477.34375,53,16,"总重/吨");
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
LODOP.ADD_PRINT_RECT(176,532.34375,210.3125,28,0,1);
LODOP.ADD_PRINT_TEXT(183,534.34375,206.3125,16,"备  注");
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);

{{#each details}}
LODOP.ADD_PRINT_RECT({{rowTop}},10,78,24,0,1);
LODOP.ADD_PRINT_RECT({{rowTop}},88,47.34375,24,0,1);
LODOP.ADD_PRINT_RECT({{rowTop}},135.34375,78,24,0,1);
LODOP.ADD_PRINT_RECT({{rowTop}},213.34375,72,24,0,1);
LODOP.ADD_PRINT_RECT({{rowTop}},285.34375,60,24,0,1);
LODOP.ADD_PRINT_RECT({{rowTop}},345.34375,64,24,0,1);
LODOP.ADD_PRINT_RECT({{rowTop}},409.34375,66,24,0,1);
LODOP.ADD_PRINT_RECT({{rowTop}},475.34375,57,24,0,1);
LODOP.ADD_PRINT_TEXT({{rowTop}}+5,12,74,16,"{{brand}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
LODOP.ADD_PRINT_TEXT({{rowTop}}+5,90,43.34375,16,"{{category}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
LODOP.ADD_PRINT_TEXT({{rowTop}}+5,137.34375,74,16,"{{material}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
LODOP.ADD_PRINT_TEXT({{rowTop}}+5,215.34375,68,16,"{{spec}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
LODOP.ADD_PRINT_TEXT({{rowTop}}+5,287.34375,56,16,"{{length}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
LODOP.ADD_PRINT_TEXT({{rowTop}}+5,347.34375,60,16,"{{quantity}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
LODOP.ADD_PRINT_TEXT({{rowTop}}+5,411.34375,62,16,"{{pieceWeightTon}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
LODOP.ADD_PRINT_TEXT({{rowTop}}+5,477.34375,53,16,"{{weightTon}}");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
{{/each}}

{{#if hasEmptyRows}}
LODOP.ADD_PRINT_TEXT({{emptyRowTop}},12,522.34375,16,"----------------以下无内容----------------");
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
LODOP.SET_PRINT_STYLEA(0,"Italic",1);
LODOP.SET_PRINT_STYLEA(0,"FontColor","#000000");
{{/if}}

LODOP.ADD_PRINT_RECT({{sumTop}},10,78,24,0,1);
LODOP.ADD_PRINT_TEXT({{sumTop}}+5,12,74,16,"合计");
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
LODOP.ADD_PRINT_RECT({{sumTop}},88,47.34375,24,0,1);
LODOP.ADD_PRINT_RECT({{sumTop}},135.34375,78,24,0,1);
LODOP.ADD_PRINT_RECT({{sumTop}},213.34375,72,24,0,1);
LODOP.ADD_PRINT_RECT({{sumTop}},285.34375,60,24,0,1);
LODOP.ADD_PRINT_RECT({{sumTop}},345.34375,64,24,0,1);
LODOP.ADD_PRINT_TEXT({{sumTop}}+5,347.34375,60,16,"{{totalQuantity}}");
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",8);
LODOP.ADD_PRINT_RECT({{sumTop}},409.34375,66,24,0,1);
LODOP.ADD_PRINT_RECT({{sumTop}},475.34375,57,24,0,1);
LODOP.ADD_PRINT_TEXT({{sumTop}}+5,477.34375,53,16,"{{totalWeight}}");
LODOP.SET_PRINT_STYLEA(0,"Bold",1);
LODOP.SET_PRINT_STYLEA(0,"Alignment",2);
LODOP.SET_PRINT_STYLEA(0,"FontSize",8);

LODOP.ADD_PRINT_RECT(204,532.34375,210.3125,264,0,1);
LODOP.ADD_PRINT_TEXT(210,538.34375,198.3125,230,"1.货物规格、材质、数量及价格在收货时当即点清，并签字生效。    2.对货物必须先行检测合格后使用，如有质量问题需方需在五日内提出书面异议，逾期视为认可，供方负责调换或协助向厂方索赔，否则供方不予处理。需方不得以质量异议为由拒付或少付货款，否则视需方违约且需方向供方支付日息万分之五付违约金。                        3.需方收货后，应当即时或合同约定时间全部付款，否则需按日息万分之五支付违约金，同时承担供方实现债权支出的一切费用。");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);
$template$
WHERE deleted_flag = FALSE
  AND bill_type = 'sales-order'
  AND template_type = 'COORD'
  AND template_name LIKE '颖捷A4打印/_带备注%' ESCAPE '/';
