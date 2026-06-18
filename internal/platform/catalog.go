package platform

type CatalogAction struct {
	Code  string `json:"code"`
	Title string `json:"title"`
}

type CatalogEntry struct {
	Code             string          `json:"code"`
	Title            string          `json:"title"`
	Group            string          `json:"group"`
	BusinessResource bool            `json:"businessResource"`
	MenuCodes        []string        `json:"menuCodes"`
	PathPrefixes     []string        `json:"pathPrefixes"`
	Actions          []CatalogAction `json:"actions"`
}

var (
	actionRead              = CatalogAction{Code: "read", Title: "查看"}
	actionCreate            = CatalogAction{Code: "create", Title: "新增"}
	actionUpdate            = CatalogAction{Code: "update", Title: "编辑"}
	actionDelete            = CatalogAction{Code: "delete", Title: "删除"}
	actionAudit             = CatalogAction{Code: "audit", Title: "审核"}
	actionExport            = CatalogAction{Code: "export", Title: "导出"}
	actionPrint             = CatalogAction{Code: "print", Title: "打印"}
	actionManagePermissions = CatalogAction{Code: "manage_permissions", Title: "配置权限"}

	crudActions     = []CatalogAction{actionRead, actionCreate, actionUpdate, actionDelete}
	businessActions = []CatalogAction{actionRead, actionCreate, actionUpdate, actionDelete, actionAudit, actionExport, actionPrint}
	reportActions   = []CatalogAction{actionRead, actionExport, actionPrint}
	readOnlyActions = []CatalogAction{actionRead}

	permissionCatalog = []CatalogEntry{
		catalogEntry("dashboard", "工作台", "工作台", false, []string{"/dashboard"}, readOnlyActions),
		catalogEntry("material", "商品资料", "主数据", true, []string{"/material", "/material-categories", "/material-category"}, businessActions),
		catalogEntry("supplier", "供应商资料", "主数据", true, []string{"/supplier"}, businessActions),
		catalogEntry("customer", "客户资料", "主数据", true, []string{"/customer"}, businessActions),
		catalogEntry("project", "项目", "主数据", true, []string{"/project", "/projects"}, crudActions),
		catalogEntry("carrier", "物流方资料", "主数据", true, []string{"/carrier"}, businessActions),
		catalogEntry("warehouse", "仓库资料", "主数据", true, []string{"/warehouse"}, businessActions),
		catalogEntry("purchase-order", "采购订单", "采购", true, []string{"/purchase-order"}, businessActions),
		catalogEntry("purchase-inbound", "采购入库", "采购", true, []string{"/purchase-inbound"}, businessActions),
		catalogEntry("sales-order", "销售订单", "销售", true, []string{"/sales-order"}, businessActions),
		catalogEntry("sales-outbound", "销售出库", "销售", true, []string{"/sales-outbound"}, businessActions),
		catalogEntry("freight-bill", "物流单", "物流", true, []string{"/freight-bill"}, businessActions),
		catalogEntry("purchase-contract", "采购合同", "合同", true, []string{"/purchase-contract"}, businessActions),
		catalogEntry("sales-contract", "销售合同", "合同", true, []string{"/sales-contract"}, businessActions),
		catalogEntry("inventory-report", "商品库存报表", "报表", true, []string{"/inventory-report"}, reportActions),
		catalogEntry("io-report", "出入库报表", "报表", true, []string{"/io-report"}, reportActions),
		catalogEntry("pending-invoice-receipt-report", "未收票报表", "报表", true, []string{"/pending-invoice-receipt-report"}, reportActions),
		catalogEntry("supplier-statement", "供应商对账单", "对账", true, []string{"/supplier-statement"}, businessActions),
		catalogEntry("customer-statement", "客户对账单", "对账", true, []string{"/customer-statement"}, businessActions),
		catalogEntry("freight-statement", "物流对账单", "对账", true, []string{"/freight-statement"}, businessActions),
		catalogEntry("receipt", "收款单", "财务", true, []string{"/receipt"}, businessActions),
		catalogEntry("payment", "付款单", "财务", true, []string{"/payment"}, businessActions),
		catalogEntry("invoice-receipt", "收票单", "财务", true, []string{"/invoice-receipt"}, businessActions),
		catalogEntry("invoice-issue", "开票单", "财务", true, []string{"/invoice-issue"}, businessActions),
		catalogEntry("ledger-adjustment", "台账调整单", "财务", true, []string{"/ledger-adjustment", "/ledger-adjustments"}, businessActions),
		catalogEntry("receivable-payable", "应收应付", "财务", true, []string{"/receivable-payable"}, reportActions),
		catalogEntry("general-setting", "通用设置", "系统", false, []string{"/general-setting", "/general-setting/upload-rule"}, []CatalogAction{actionRead, actionUpdate}),
		catalogEntry("company-setting", "公司信息", "系统", false, []string{"/company-setting"}, crudActions),
		catalogEntry("operation-log", "操作日志", "系统", false, []string{"/operation-log"}, readOnlyActions),
		catalogEntry("department", "部门", "主数据", false, []string{"/department"}, crudActions),
		catalogEntry("user-account", "用户账户", "系统", false, []string{"/user-account"}, crudActions),
		catalogEntry("permission", "权限管理", "系统", false, []string{"/permission"}, readOnlyActions),
		catalogEntry("role", "角色", "系统", false, []string{"/role-setting", "/role-action-editor"}, []CatalogAction{actionRead, actionCreate, actionUpdate, actionDelete, actionManagePermissions}),
		catalogEntry("access-control", "访问控制", "系统", false, []string{"/access-control"}, readOnlyActions),
		catalogEntry("database", "数据库管理", "系统", false, []string{"/database", "/system/database"}, []CatalogAction{actionRead, actionUpdate, actionExport}),
		catalogEntry("session", "会话管理", "系统", false, []string{"/auth/refresh-token"}, []CatalogAction{actionRead, actionUpdate}),
		catalogEntry("api-key", "API Key 管理", "系统", false, []string{"/auth/api-key"}, []CatalogAction{actionRead, actionCreate, actionUpdate}),
		catalogEntry("security-key", "安全密钥管理", "系统", false, []string{"/system/security-key"}, []CatalogAction{actionRead, actionUpdate}),
		catalogEntry("print-template", "打印模板", "系统", false, []string{"/print-template"}, crudActions),
	}
)

func PermissionCatalog() []CatalogEntry {
	return permissionCatalog
}

func ResourceForMenuCode(menuCode string) (string, bool) {
	return resourceForMenuCode(menuCode)
}

func catalogEntry(code string, title string, group string, businessResource bool, pathPrefixes []string, actions []CatalogAction) CatalogEntry {
	return CatalogEntry{
		Code:             code,
		Title:            title,
		Group:            group,
		BusinessResource: businessResource,
		MenuCodes:        menuCodesForCatalogEntry(code),
		PathPrefixes:     append([]string(nil), pathPrefixes...),
		Actions:          append([]CatalogAction(nil), actions...),
	}
}

func menuCodesForCatalogEntry(code string) []string {
	switch code {
	case "user-account", "permission", "role":
		return []string{}
	case "material":
		return []string{"material", "material-categories", "material-category"}
	default:
		return []string{code}
	}
}

func resourceForMenuCode(menuCode string) (string, bool) {
	menuCode = normalizeResourceCode(menuCode)
	for _, entry := range permissionCatalog {
		for _, code := range entry.MenuCodes {
			if normalizeResourceCode(code) == menuCode {
				return entry.Code, true
			}
		}
	}
	return "", false
}
