package platform

import "time"

type PageQuery struct {
	Page      int
	Size      int
	SortBy    string
	Direction string
}

type PageResponse[T any] struct {
	Content       []T   `json:"content"`
	TotalElements int64 `json:"totalElements"`
	TotalPages    int   `json:"totalPages"`
	CurrentPage   int   `json:"currentPage"`
	PageSize      int   `json:"pageSize"`
	HasMore       bool  `json:"hasMore"`
}

type FileDownloadResponse struct {
	Filename    string
	ContentType string
	Content     []byte
}

func NewPageResponse[T any](content []T, total int64, query PageQuery) PageResponse[T] {
	size := query.Size
	if size <= 0 {
		size = 20
	}
	totalPages := 0
	if total > 0 {
		totalPages = int((total + int64(size) - 1) / int64(size))
	}
	if content == nil {
		content = []T{}
	}
	return PageResponse[T]{
		Content:       content,
		TotalElements: total,
		TotalPages:    totalPages,
		CurrentPage:   query.Page,
		PageSize:      size,
		HasMore:       query.Page+1 < totalPages,
	}
}

type UserAccountAdminRequest struct {
	LoginName         string   `json:"loginName"`
	Password          string   `json:"password"`
	UserName          string   `json:"userName"`
	Mobile            string   `json:"mobile"`
	DepartmentID      *int64   `json:"departmentId"`
	RoleNames         []string `json:"roleNames"`
	RoleIDs           []int64  `json:"roleIds"`
	DataScope         string   `json:"dataScope"`
	PermissionSummary string   `json:"permissionSummary"`
	Status            string   `json:"status"`
	Remark            string   `json:"remark"`
}

type UserAccountAdminResponse struct {
	ID                int64      `json:"id"`
	LoginName         string     `json:"loginName"`
	UserName          string     `json:"userName"`
	Mobile            string     `json:"mobile"`
	DepartmentID      *int64     `json:"departmentId"`
	DepartmentName    string     `json:"departmentName"`
	RoleNames         []string   `json:"roleNames"`
	RoleIDs           []int64    `json:"roleIds"`
	DataScope         string     `json:"dataScope"`
	PermissionSummary string     `json:"permissionSummary"`
	LastLoginDate     *time.Time `json:"lastLoginDate"`
	Status            string     `json:"status"`
	Remark            string     `json:"remark"`
	TotpEnabled       bool       `json:"totpEnabled"`
}

type UserAccountCreateResponse struct {
	User            UserAccountAdminResponse `json:"user"`
	InitialPassword string                   `json:"initialPassword"`
}

type LoginNameAvailabilityResponse struct {
	Available bool   `json:"available"`
	Message   string `json:"message"`
}

type UserAccountPreferencesPayload struct {
	Pages map[string]UserListColumnSettingsPayload `json:"pages"`
}

type UserListColumnSettingsPayload struct {
	OrderedKeys []string `json:"orderedKeys"`
	HiddenKeys  []string `json:"hiddenKeys"`
}

type RefreshTokenAdminResponse struct {
	ID           int64      `json:"id"`
	UserID       int64      `json:"userId"`
	LoginName    string     `json:"loginName"`
	UserName     string     `json:"userName"`
	TokenID      string     `json:"tokenId"`
	LoginIP      string     `json:"loginIp"`
	DeviceInfo   string     `json:"deviceInfo"`
	CreatedAt    time.Time  `json:"createdAt"`
	ExpiresAt    time.Time  `json:"expiresAt"`
	RevokedAt    *time.Time `json:"revokedAt"`
	Status       string     `json:"status"`
	LastActiveAt *time.Time `json:"lastActiveAt"`
	Online       bool       `json:"online"`
}

type RefreshTokenSessionSummaryResponse struct {
	OnlineUsers    int64 `json:"onlineUsers"`
	OnlineSessions int64 `json:"onlineSessions"`
	ActiveSessions int64 `json:"activeSessions"`
}

type ApiKeyRequest struct {
	KeyName          string   `json:"keyName"`
	UsageScope       string   `json:"usageScope"`
	AllowedResources []string `json:"allowedResources"`
	AllowedActions   []string `json:"allowedActions"`
	ExpireDays       *int64   `json:"expireDays"`
}

type ApiKeyResponse struct {
	ID               int64      `json:"id"`
	UserID           int64      `json:"userId"`
	LoginName        string     `json:"loginName"`
	UserName         string     `json:"userName"`
	KeyName          string     `json:"keyName"`
	UsageScope       string     `json:"usageScope"`
	AllowedResources []string   `json:"allowedResources"`
	AllowedActions   []string   `json:"allowedActions"`
	KeyPrefix        string     `json:"keyPrefix"`
	RawKey           string     `json:"rawKey,omitempty"`
	CreatedAt        time.Time  `json:"createdAt"`
	ExpiresAt        *time.Time `json:"expiresAt"`
	LastUsedAt       *time.Time `json:"lastUsedAt"`
	Status           string     `json:"status"`
}

type ApiKeyUserOptionResponse struct {
	ID        int64  `json:"id"`
	LoginName string `json:"loginName"`
	UserName  string `json:"userName"`
	Mobile    string `json:"mobile"`
}

type ApiKeyResourceOptionResponse struct {
	Code  string `json:"code"`
	Title string `json:"title"`
	Group string `json:"group"`
}

type ApiKeyActionOptionResponse struct {
	Code  string `json:"code"`
	Title string `json:"title"`
}

type PermissionEntryResponse struct {
	ID             int64  `json:"id"`
	PermissionCode string `json:"permissionCode"`
	PermissionName string `json:"permissionName"`
	ModuleName     string `json:"moduleName"`
	PermissionType string `json:"permissionType"`
	ActionName     string `json:"actionName"`
	ScopeName      string `json:"scopeName"`
	ResourceKey    string `json:"resourceKey"`
	Status         string `json:"status"`
	Remark         string `json:"remark"`
}

type DepartmentRequest struct {
	DepartmentCode string `json:"departmentCode"`
	DepartmentName string `json:"departmentName"`
	ParentID       *int64 `json:"parentId"`
	ManagerName    string `json:"managerName"`
	ContactPhone   string `json:"contactPhone"`
	SortOrder      *int   `json:"sortOrder"`
	Status         string `json:"status"`
	Remark         string `json:"remark"`
}

type DepartmentResponse struct {
	ID             int64  `json:"id"`
	DepartmentCode string `json:"departmentCode"`
	DepartmentName string `json:"departmentName"`
	ParentID       *int64 `json:"parentId"`
	ParentName     string `json:"parentName"`
	ManagerName    string `json:"managerName"`
	ContactPhone   string `json:"contactPhone"`
	SortOrder      int    `json:"sortOrder"`
	Status         string `json:"status"`
	Remark         string `json:"remark"`
}

type DepartmentOptionResponse struct {
	ID             int64  `json:"id"`
	DepartmentCode string `json:"departmentCode"`
	DepartmentName string `json:"departmentName"`
}

type MaterialCategoryRequest struct {
	CategoryCode          string `json:"categoryCode"`
	CategoryName          string `json:"categoryName"`
	SortOrder             *int   `json:"sortOrder"`
	PurchaseWeighRequired *bool  `json:"purchaseWeighRequired"`
	Status                string `json:"status"`
	Remark                string `json:"remark"`
}

type MaterialCategoryResponse struct {
	ID                    string `json:"id"`
	CategoryCode          string `json:"categoryCode"`
	CategoryName          string `json:"categoryName"`
	SortOrder             int    `json:"sortOrder"`
	PurchaseWeighRequired bool   `json:"purchaseWeighRequired"`
	Status                string `json:"status"`
	Remark                string `json:"remark"`
}

type MaterialCategoryOptionResponse struct {
	Value                 string `json:"value"`
	Label                 string `json:"label"`
	PurchaseWeighRequired bool   `json:"purchaseWeighRequired"`
}

type SupplierRequest struct {
	SupplierCode string  `json:"supplierCode"`
	SupplierName string  `json:"supplierName"`
	ContactName  *string `json:"contactName"`
	ContactPhone *string `json:"contactPhone"`
	City         *string `json:"city"`
	Status       string  `json:"status"`
	Remark       *string `json:"remark"`
}

type SupplierResponse struct {
	ID           int64   `json:"id"`
	SupplierCode string  `json:"supplierCode"`
	SupplierName string  `json:"supplierName"`
	ContactName  *string `json:"contactName"`
	ContactPhone *string `json:"contactPhone"`
	City         *string `json:"city"`
	Status       string  `json:"status"`
	Remark       *string `json:"remark"`
}

type SupplierOptionResponse struct {
	ID    int64  `json:"id"`
	Label string `json:"label"`
	Value string `json:"value"`
}

type CustomerRequest struct {
	CustomerCode    string  `json:"customerCode"`
	CustomerName    string  `json:"customerName"`
	ContactName     *string `json:"contactName"`
	ContactPhone    *string `json:"contactPhone"`
	City            *string `json:"city"`
	SettlementMode  *string `json:"settlementMode"`
	ProjectName     string  `json:"projectName"`
	ProjectNameAbbr *string `json:"projectNameAbbr"`
	ProjectAddress  *string `json:"projectAddress"`
	Status          string  `json:"status"`
	Remark          *string `json:"remark"`
}

type CustomerResponse struct {
	ID              int64   `json:"id"`
	CustomerCode    string  `json:"customerCode"`
	CustomerName    string  `json:"customerName"`
	ContactName     *string `json:"contactName"`
	ContactPhone    *string `json:"contactPhone"`
	City            *string `json:"city"`
	SettlementMode  *string `json:"settlementMode"`
	ProjectName     string  `json:"projectName"`
	ProjectNameAbbr *string `json:"projectNameAbbr"`
	ProjectAddress  *string `json:"projectAddress"`
	Status          string  `json:"status"`
	Remark          *string `json:"remark"`
}

type CustomerOptionResponse struct {
	ID              int64   `json:"id"`
	Label           string  `json:"label"`
	Value           string  `json:"value"`
	CustomerCode    string  `json:"customerCode"`
	CustomerName    string  `json:"customerName"`
	ProjectName     string  `json:"projectName"`
	ProjectNameAbbr *string `json:"projectNameAbbr"`
}

type ProjectRequest struct {
	ProjectCode     string  `json:"projectCode"`
	ProjectName     string  `json:"projectName"`
	ProjectNameAbbr *string `json:"projectNameAbbr"`
	ProjectAddress  *string `json:"projectAddress"`
	ProjectManager  *string `json:"projectManager"`
	CustomerCode    string  `json:"customerCode"`
	Status          string  `json:"status"`
	Remark          *string `json:"remark"`
}

type ProjectResponse struct {
	ID              int64   `json:"id"`
	ProjectCode     string  `json:"projectCode"`
	ProjectName     string  `json:"projectName"`
	ProjectNameAbbr *string `json:"projectNameAbbr"`
	ProjectAddress  *string `json:"projectAddress"`
	ProjectManager  *string `json:"projectManager"`
	CustomerCode    string  `json:"customerCode"`
	Status          string  `json:"status"`
	Remark          *string `json:"remark"`
}

type VehicleItem struct {
	Plate   string  `json:"plate"`
	Contact *string `json:"contact"`
	Phone   *string `json:"phone"`
	Remark  *string `json:"remark"`
}

type VehicleInfo struct {
	ID      int64   `json:"id"`
	Plate   string  `json:"plate"`
	Contact *string `json:"contact"`
	Phone   *string `json:"phone"`
	Remark  *string `json:"remark"`
}

type CarrierRequest struct {
	CarrierCode  string        `json:"carrierCode"`
	CarrierName  string        `json:"carrierName"`
	ContactName  *string       `json:"contactName"`
	ContactPhone *string       `json:"contactPhone"`
	VehicleType  *string       `json:"vehicleType"`
	Vehicles     []VehicleItem `json:"vehicles"`
	PriceMode    *string       `json:"priceMode"`
	Status       string        `json:"status"`
	Remark       *string       `json:"remark"`
}

type CarrierResponse struct {
	ID           int64         `json:"id"`
	CarrierCode  string        `json:"carrierCode"`
	CarrierName  string        `json:"carrierName"`
	ContactName  *string       `json:"contactName"`
	ContactPhone *string       `json:"contactPhone"`
	VehicleType  *string       `json:"vehicleType"`
	Vehicles     []VehicleInfo `json:"vehicles"`
	PriceMode    *string       `json:"priceMode"`
	Status       string        `json:"status"`
	Remark       *string       `json:"remark"`
}

type CarrierOptionResponse struct {
	ID            int64    `json:"id"`
	Label         string   `json:"label"`
	Value         string   `json:"value"`
	VehiclePlates []string `json:"vehiclePlates"`
}

type OptionResponse struct {
	Label string `json:"label"`
	Value string `json:"value"`
}

type WarehouseRequest struct {
	WarehouseCode string  `json:"warehouseCode"`
	WarehouseName string  `json:"warehouseName"`
	WarehouseType string  `json:"warehouseType"`
	ContactName   *string `json:"contactName"`
	ContactPhone  *string `json:"contactPhone"`
	Address       *string `json:"address"`
	Status        string  `json:"status"`
	Remark        *string `json:"remark"`
}

type WarehouseResponse struct {
	ID            int64   `json:"id"`
	WarehouseCode string  `json:"warehouseCode"`
	WarehouseName string  `json:"warehouseName"`
	WarehouseType string  `json:"warehouseType"`
	ContactName   *string `json:"contactName"`
	ContactPhone  *string `json:"contactPhone"`
	Address       *string `json:"address"`
	Status        string  `json:"status"`
	Remark        *string `json:"remark"`
}

type IoReportFilter struct {
	Keyword      string
	BusinessType string
	StartDate    string
	EndDate      string
}

type IoReportResponse struct {
	ID            int64   `json:"id"`
	BusinessDate  string  `json:"businessDate"`
	BusinessType  string  `json:"businessType"`
	SourceNo      string  `json:"sourceNo"`
	MaterialCode  string  `json:"materialCode"`
	Brand         string  `json:"brand"`
	Material      string  `json:"material"`
	Category      string  `json:"category"`
	Spec          string  `json:"spec"`
	Length        string  `json:"length"`
	WarehouseName string  `json:"warehouseName"`
	BatchNo       string  `json:"batchNo"`
	InQuantity    int     `json:"inQuantity"`
	OutQuantity   int     `json:"outQuantity"`
	QuantityUnit  string  `json:"quantityUnit"`
	InWeightTon   float64 `json:"inWeightTon"`
	OutWeightTon  float64 `json:"outWeightTon"`
	Unit          string  `json:"unit"`
	Remark        string  `json:"remark"`
}

type InventoryReportFilter struct {
	Keyword       string
	WarehouseName string
	Category      string
}

type InventoryReportResponse struct {
	ID             int64   `json:"id"`
	MaterialCode   string  `json:"materialCode"`
	Brand          string  `json:"brand"`
	Material       string  `json:"material"`
	Category       string  `json:"category"`
	Spec           string  `json:"spec"`
	Length         string  `json:"length"`
	WarehouseName  string  `json:"warehouseName"`
	BatchNo        string  `json:"batchNo"`
	Quantity       int     `json:"quantity"`
	QuantityUnit   string  `json:"quantityUnit"`
	WeightTon      float64 `json:"weightTon"`
	Unit           string  `json:"unit"`
	PieceWeightTon float64 `json:"pieceWeightTon"`
}

type PendingInvoiceReceiptReportFilter struct {
	Keyword      string
	SupplierName string
	StartDate    string
	EndDate      string
}

type PendingInvoiceReceiptReportResponse struct {
	ID                       int64   `json:"id"`
	OrderNo                  string  `json:"orderNo"`
	SupplierName             string  `json:"supplierName"`
	InvoiceTitle             string  `json:"invoiceTitle"`
	OrderDate                string  `json:"orderDate"`
	MaterialCode             string  `json:"materialCode"`
	Brand                    string  `json:"brand"`
	Material                 string  `json:"material"`
	Category                 string  `json:"category"`
	Spec                     string  `json:"spec"`
	Length                   string  `json:"length"`
	OrderQuantity            int     `json:"orderQuantity"`
	QuantityUnit             string  `json:"quantityUnit"`
	OrderWeightTon           float64 `json:"orderWeightTon"`
	ReceivedInvoiceWeightTon float64 `json:"receivedInvoiceWeightTon"`
	PendingInvoiceWeightTon  float64 `json:"pendingInvoiceWeightTon"`
	UnitPrice                float64 `json:"unitPrice"`
	OrderAmount              float64 `json:"orderAmount"`
	ReceivedInvoiceAmount    float64 `json:"receivedInvoiceAmount"`
	PendingInvoiceAmount     float64 `json:"pendingInvoiceAmount"`
	Status                   string  `json:"status"`
}

type OperationLogResponse struct {
	ID            int64     `json:"id"`
	LogNo         string    `json:"logNo"`
	OperatorName  string    `json:"operatorName"`
	LoginName     string    `json:"loginName"`
	AuthType      string    `json:"authType"`
	ModuleName    string    `json:"moduleName"`
	ActionType    string    `json:"actionType"`
	BusinessNo    string    `json:"businessNo"`
	RecordID      *int64    `json:"recordId"`
	ModuleKey     string    `json:"moduleKey"`
	RequestMethod string    `json:"requestMethod"`
	RequestPath   string    `json:"requestPath"`
	ClientIP      string    `json:"clientIp"`
	ResultStatus  string    `json:"resultStatus"`
	OperationTime time.Time `json:"operationTime"`
	Remark        string    `json:"remark"`
}

type OperationLogFilter struct {
	Keyword      string
	ModuleName   string
	ActionType   string
	ResultStatus string
	StartTime    string
	EndTime      string
	RecordID     *int64
	AuthType     string
}

type PrintTemplateRequest struct {
	BillType     string `json:"billType"`
	TemplateName string `json:"templateName"`
	TemplateCode string `json:"templateCode"`
	TemplateHtml string `json:"templateHtml"`
	TemplateType string `json:"templateType"`
	Engine       string `json:"engine"`
	AssetRef     string `json:"assetRef"`
	VersionNo    *int   `json:"versionNo"`
	Status       string `json:"status"`
}

type PrintTemplateResponse struct {
	ID             string  `json:"id"`
	TemplateName   string  `json:"templateName"`
	TemplateCode   string  `json:"templateCode"`
	TemplateHtml   string  `json:"templateHtml"`
	BillType       string  `json:"billType"`
	TemplateType   string  `json:"templateType"`
	Engine         string  `json:"engine"`
	AssetRef       *string `json:"assetRef"`
	VersionNo      int     `json:"versionNo"`
	Status         string  `json:"status"`
	SyncMode       string  `json:"syncMode"`
	SourceRef      *string `json:"sourceRef"`
	SourceChecksum *string `json:"sourceChecksum"`
	CreateTime     int64   `json:"createTime"`
	UpdateTime     *int64  `json:"updateTime"`
}

type GeneralSettingResponse struct {
	ID           int64  `json:"id"`
	SettingCode  string `json:"settingCode"`
	SettingName  string `json:"settingName"`
	BillName     string `json:"billName"`
	Prefix       string `json:"prefix"`
	DateRule     string `json:"dateRule"`
	SerialLength int    `json:"serialLength"`
	ResetRule    string `json:"resetRule"`
	SampleNo     string `json:"sampleNo"`
	Status       string `json:"status"`
	Remark       string `json:"remark"`
	RuleType     string `json:"ruleType"`
	ModuleKey    string `json:"moduleKey"`
}

type NoRuleRequest struct {
	SettingCode  string `json:"settingCode"`
	SettingName  string `json:"settingName"`
	BillName     string `json:"billName"`
	Prefix       string `json:"prefix"`
	DateRule     string `json:"dateRule"`
	SerialLength *int   `json:"serialLength"`
	ResetRule    string `json:"resetRule"`
	SampleNo     string `json:"sampleNo"`
	Status       string `json:"status"`
	Remark       string `json:"remark"`
}

type NoRuleResponse struct {
	ID           int64  `json:"id"`
	SettingCode  string `json:"settingCode"`
	SettingName  string `json:"settingName"`
	BillName     string `json:"billName"`
	Prefix       string `json:"prefix"`
	DateRule     string `json:"dateRule"`
	SerialLength int    `json:"serialLength"`
	ResetRule    string `json:"resetRule"`
	SampleNo     string `json:"sampleNo"`
	Status       string `json:"status"`
	Remark       string `json:"remark"`
}

type NoRuleGenerateResponse struct {
	ModuleKey   string  `json:"moduleKey"`
	GeneratedNo string  `json:"generatedNo"`
	GeneratedID *string `json:"generatedId"`
}

type StatementGeneratorRulesResponse struct {
	CustomerStatementReceiptAmountZero bool `json:"customerStatementReceiptAmountZero"`
	SupplierStatementFullPayment       bool `json:"supplierStatementFullPayment"`
}

type UploadRuleRequest struct {
	RenamePattern string `json:"renamePattern"`
	Status        string `json:"status"`
	Remark        string `json:"remark"`
}

type UploadRuleResponse struct {
	ID              int64  `json:"id"`
	ModuleKey       string `json:"moduleKey"`
	ModuleName      string `json:"moduleName"`
	RuleCode        string `json:"ruleCode"`
	RuleName        string `json:"ruleName"`
	RenamePattern   string `json:"renamePattern"`
	Status          string `json:"status"`
	Remark          string `json:"remark"`
	PreviewFileName string `json:"previewFileName"`
}

type SecurityKeyOverviewResponse struct {
	JWT  SecurityKeyItemResponse `json:"jwt"`
	TOTP SecurityKeyItemResponse `json:"totp"`
}

type SecurityKeyItemResponse struct {
	KeyCode              string     `json:"keyCode"`
	KeyName              string     `json:"keyName"`
	Source               string     `json:"source"`
	ActiveVersion        int        `json:"activeVersion"`
	ActiveFingerprint    string     `json:"activeFingerprint"`
	ActivatedAt          *time.Time `json:"activatedAt"`
	RetiredVersionCount  int        `json:"retiredVersionCount"`
	ProtectedRecordCount int        `json:"protectedRecordCount"`
	Remark               string     `json:"remark"`
}

type SecurityKeyRotateResponse struct {
	KeyCode              string    `json:"keyCode"`
	Source               string    `json:"source"`
	ActiveVersion        int       `json:"activeVersion"`
	ActiveFingerprint    string    `json:"activeFingerprint"`
	RotatedAt            time.Time `json:"rotatedAt"`
	ProcessedRecordCount int       `json:"processedRecordCount"`
	RetiredVersionCount  int       `json:"retiredVersionCount"`
	Remark               string    `json:"remark"`
}

type RoleSettingResponse struct {
	ID                int64    `json:"id"`
	RoleCode          string   `json:"roleCode"`
	RoleName          string   `json:"roleName"`
	RoleType          string   `json:"roleType"`
	DataScope         string   `json:"dataScope"`
	PermissionCodes   []string `json:"permissionCodes"`
	PermissionCount   int      `json:"permissionCount"`
	PermissionSummary string   `json:"permissionSummary"`
	UserCount         int      `json:"userCount"`
	Status            string   `json:"status"`
	Remark            string   `json:"remark"`
}

type RoleSettingRequest struct {
	RoleCode          string `json:"roleCode"`
	RoleName          string `json:"roleName"`
	RoleType          string `json:"roleType"`
	DataScope         string `json:"dataScope"`
	PermissionSummary string `json:"permissionSummary"`
	UserCount         *int   `json:"userCount"`
	Status            string `json:"status"`
	Remark            string `json:"remark"`
}

type RolePermissionItem struct {
	Resource string `json:"resource"`
	Action   string `json:"action"`
}

type RoleTemplate struct {
	Name        string                        `json:"name"`
	Description string                        `json:"description"`
	Permissions []RoleTemplatePermissionEntry `json:"permissions"`
}

type RoleTemplatePermissionEntry struct {
	Resource string   `json:"resource"`
	Actions  []string `json:"actions"`
}
