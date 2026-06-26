UPDATE sys_print_template
SET template_html = replace(
        template_html,
        'LODOP.ADD_PRINT_TEXT(144,538.34375,198.3125,230,"1.货物规格、材质、数量及价格在收货时当即点清，并签字生效。    2.对货物必须先行检测合格后使用，如有质量问题需方需在五日内提出书面异议，逾期视为认可，供方负责调换或协助向厂方索赔，否则供方不予处理。需方不得以质量异议为由拒付或少付货款，否则视需方违约且需方向供方支付日息万分之五付违约金。                        3.需方收货后，应当即时或合同约定时间全部付款，否则需按日息万分之五支付违约金，同时承担供方实现债权支出的一切费用。");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);',
        'LODOP.ADD_PRINT_TEXT(144,538.34375,198.3125,252,"1.货物规格、材质、数量及价格在收货时当即点清，并签字生效。    2.对货物必须先行检测合格后使用，如有质量问题需方需在五日内提出书面异议，逾期视为认可，供方负责调换或协助向厂方索赔，否则供方不予处理。需方不得以质量异议为由拒付或少付货款，否则视需方违约且需方向供方支付日息万分之五付违约金。                        3.需方收货后，应当即时或合同约定时间全部付款，否则需按日息万分之五支付违约金，同时承担供方实现债权支出的一切费用。");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);'
    ),
    updated_at = now()
WHERE deleted_flag = FALSE
  AND bill_type = 'sales-outbound'
  AND template_type = 'COORD'
  AND template_name = '颖捷A4打印'
  AND template_html LIKE '%LODOP.ADD_PRINT_TEXT(144,538.34375,198.3125,230,"1.货物规格%3.需方收货后，应当即时或合同约定时间全部付款%';

UPDATE sys_print_template
SET template_html = replace(
        template_html,
        'LODOP.ADD_PRINT_TEXT(210,538.34375,198.3125,230,"1.货物规格、材质、数量及价格在收货时当即点清，并签字生效。    2.对货物必须先行检测合格后使用，如有质量问题需方需在五日内提出书面异议，逾期视为认可，供方负责调换或协助向厂方索赔，否则供方不予处理。需方不得以质量异议为由拒付或少付货款，否则视需方违约且需方向供方支付日息万分之五付违约金。                        3.需方收货后，应当即时或合同约定时间全部付款，否则需按日息万分之五支付违约金，同时承担供方实现债权支出的一切费用。");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);',
        'LODOP.ADD_PRINT_TEXT(210,538.34375,198.3125,252,"1.货物规格、材质、数量及价格在收货时当即点清，并签字生效。    2.对货物必须先行检测合格后使用，如有质量问题需方需在五日内提出书面异议，逾期视为认可，供方负责调换或协助向厂方索赔，否则供方不予处理。需方不得以质量异议为由拒付或少付货款，否则视需方违约且需方向供方支付日息万分之五付违约金。                        3.需方收货后，应当即时或合同约定时间全部付款，否则需按日息万分之五支付违约金，同时承担供方实现债权支出的一切费用。");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);'
    ),
    updated_at = now()
WHERE deleted_flag = FALSE
  AND bill_type IN ('sales-order', 'sales-outbound')
  AND template_type = 'COORD'
  AND template_name LIKE '颖捷A4打印/_带备注%' ESCAPE '/'
  AND template_html LIKE '%LODOP.ADD_PRINT_TEXT(210,538.34375,198.3125,230,"1.货物规格%3.需方收货后，应当即时或合同约定时间全部付款%';

UPDATE sys_print_template
SET template_html = replace(
        template_html,
        'LODOP.ADD_PRINT_TEXT(dataTop+6,remarkLeft+6,remarkW-12,230,"1.货物规格、材质、数量及价格在收货时当即点清，并签字生效。    2.对货物必须先行检测合格后使用，如有质量问题需方需在五日内提出书面异议，逾期视为认可，供方负责调换或协助向厂方索赔，否则供方不予处理。需方不得以质量异议为由拒付或少付货款，否则视需方违约且需方向供方支付日息万分之五付违约金。                        3.需方收货后，应当即时或合同约定时间全部付款，否则需按日息万分之五支付违约金，同时承担供方实现债权支出的一切费用。");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);',
        'LODOP.ADD_PRINT_TEXT(dataTop+6,remarkLeft+6,remarkW-12,252,"1.货物规格、材质、数量及价格在收货时当即点清，并签字生效。    2.对货物必须先行检测合格后使用，如有质量问题需方需在五日内提出书面异议，逾期视为认可，供方负责调换或协助向厂方索赔，否则供方不予处理。需方不得以质量异议为由拒付或少付货款，否则视需方违约且需方向供方支付日息万分之五付违约金。                        3.需方收货后，应当即时或合同约定时间全部付款，否则需按日息万分之五支付违约金，同时承担供方实现债权支出的一切费用。");
LODOP.SET_PRINT_STYLEA(0,"FontSize",9);'
    ),
    updated_at = now()
WHERE deleted_flag = FALSE
  AND bill_type = 'sales-order'
  AND template_name LIKE '颖捷A4打印/_带备注%' ESCAPE '/'
  AND template_html LIKE '%LODOP.ADD_PRINT_TEXT(dataTop+6,remarkLeft+6,remarkW-12,230,"1.货物规格%3.需方收货后，应当即时或合同约定时间全部付款%';
