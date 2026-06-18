package httpapi

func resourceLabels() map[string]string {
	return map[string]string{
		"dashboard":                      "工作台",
		"material":                       "商品资料",
		"supplier":                       "供应商资料",
		"customer":                       "客户资料",
		"project":                        "项目",
		"carrier":                        "物流方资料",
		"warehouse":                      "仓库资料",
		"purchase-order":                 "采购订单",
		"purchase-inbound":               "采购入库",
		"sales-order":                    "销售订单",
		"sales-outbound":                 "销售出库",
		"freight-bill":                   "物流单",
		"purchase-contract":              "采购合同",
		"sales-contract":                 "销售合同",
		"inventory-report":               "商品库存报表",
		"io-report":                      "出入库报表",
		"pending-invoice-receipt-report": "未收票报表",
		"supplier-statement":             "供应商对账单",
		"customer-statement":             "客户对账单",
		"freight-statement":              "物流对账单",
		"receipt":                        "收款单",
		"payment":                        "付款单",
		"invoice-receipt":                "收票单",
		"invoice-issue":                  "开票单",
		"ledger-adjustment":              "台账调整单",
		"receivable-payable":             "应收应付",
		"general-setting":                "通用设置",
		"company-setting":                "公司信息",
		"operation-log":                  "操作日志",
		"department":                     "部门",
		"user-account":                   "用户账户",
		"permission":                     "权限管理",
		"role":                           "角色",
		"access-control":                 "访问控制",
		"database":                       "数据库管理",
		"session":                        "会话管理",
		"api-key":                        "API Key 管理",
		"security-key":                   "安全密钥管理",
		"print-template":                 "打印模板",
	}
}

func actionLabels() map[string]string {
	return map[string]string{
		"read":               "查看",
		"create":             "新增",
		"update":             "编辑",
		"delete":             "删除",
		"audit":              "审核",
		"export":             "导出",
		"print":              "打印",
		"manage_permissions": "配置权限",
	}
}
