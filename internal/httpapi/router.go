package httpapi

import (
	"context"
	"log/slog"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/leo-erp/leo/internal/config"
	"github.com/leo-erp/leo/internal/platform"
)

type HealthChecker interface {
	Check(ctx context.Context) platform.Health
}

type SetupStatusProvider interface {
	Status(ctx context.Context) (platform.SetupStatus, error)
}

type SetupInitializer interface {
	Initialize(ctx context.Context, req platform.InitialSetupSubmitRequest) (platform.InitialSetupSubmitResponse, error)
	SetupAdminTotp(ctx context.Context, req platform.InitialSetupTotpSetupRequest) (platform.TotpSetupResponse, error)
	ConfigureAdmin(ctx context.Context, req platform.InitialSetupAdminSubmitRequest) (platform.InitialSetupSubmitResponse, error)
	ConfigureCompany(ctx context.Context, req platform.InitialSetupCompanyRequest) (platform.InitialSetupSubmitResponse, error)
}

type CaptchaGenerator interface {
	Generate(ctx context.Context) (platform.Captcha, error)
}

type AuthProvider interface {
	Login(ctx context.Context, req platform.LoginRequest, clientIP, userAgent string, captcha platform.CaptchaVerifier) (platform.LoginResponseBody, error)
	Login2FA(ctx context.Context, req platform.Login2FARequest, clientIP, userAgent string) (platform.TokenResponse, error)
	Refresh(ctx context.Context, refreshToken, clientIP, userAgent string) (platform.TokenResponse, error)
	Logout(ctx context.Context, refreshToken string) error
	AuthenticateAccessToken(ctx context.Context, rawToken string) (platform.AuthenticatedPrincipal, error)
	CurrentUserSecurityStatus(ctx context.Context, userID int64) (platform.CurrentUserSecurityResponse, error)
	VerifyCurrentUserTOTP(ctx context.Context, userID int64, totpCode string) error
}

type AccountSecurityWriter interface {
	ChangeCurrentUserPassword(ctx context.Context, userID int64, currentPassword, newPassword string) error
	SetupCurrentUser2FA(ctx context.Context, userID int64) (qrCodeBase64 string, secret string, err error)
	EnableCurrentUser2FA(ctx context.Context, userID int64, totpCode string) (platform.CurrentUserSecurityResponse, error)
	DisableCurrentUser2FA(ctx context.Context, userID int64, totpCode string) (platform.CurrentUserSecurityResponse, error)
}

type PermissionChecker interface {
	Require(ctx context.Context, userID int64, resource string, action string) error
}

type PermissionDataScopeResolver interface {
	DataScope(ctx context.Context, userID int64, resource string, action string) (platform.DataScope, error)
}

type DashboardSummaryProvider interface {
	Summary(ctx context.Context, userID int64) (platform.DashboardSummary, error)
}

type DepartmentProvider interface {
	Page(ctx context.Context, query platform.PageQuery, keyword string, status string) (platform.PageResponse[platform.DepartmentResponse], error)
	Options(ctx context.Context) ([]platform.DepartmentOptionResponse, error)
	Detail(ctx context.Context, id int64) (platform.DepartmentResponse, error)
	Create(ctx context.Context, request platform.DepartmentRequest) (platform.DepartmentResponse, error)
	Update(ctx context.Context, id int64, request platform.DepartmentRequest) (platform.DepartmentResponse, error)
	Delete(ctx context.Context, id int64) error
}

type OperationLogProvider interface {
	Page(ctx context.Context, query platform.PageQuery, filter platform.OperationLogFilter) (platform.PageResponse[platform.OperationLogResponse], error)
}

type GeneralSettingProvider interface {
	Page(ctx context.Context, query platform.PageQuery, keyword string, status string) (platform.PageResponse[platform.GeneralSettingResponse], error)
	PublicDisplaySwitches(ctx context.Context) ([]platform.GeneralSettingResponse, error)
	PublicClientSettings(ctx context.Context) ([]platform.GeneralSettingResponse, error)
	StatementGeneratorRules(ctx context.Context) (platform.StatementGeneratorRulesResponse, error)
	Detail(ctx context.Context, id int64) (platform.NoRuleResponse, error)
	NextNumber(ctx context.Context, moduleKey string) (platform.NoRuleGenerateResponse, error)
	Create(ctx context.Context, request platform.NoRuleRequest) (platform.NoRuleResponse, error)
	Update(ctx context.Context, id int64, request platform.NoRuleRequest) (platform.NoRuleResponse, error)
	Delete(ctx context.Context, id int64) error
}

type UploadRuleProvider interface {
	Detail(ctx context.Context, moduleKey string) (platform.UploadRuleResponse, error)
	Update(ctx context.Context, moduleKey string, request platform.UploadRuleRequest) (platform.UploadRuleResponse, error)
}

type SecurityKeyProvider interface {
	Overview(ctx context.Context) (platform.SecurityKeyOverviewResponse, error)
	RotateJWT(ctx context.Context) (platform.SecurityKeyRotateResponse, error)
	RotateTOTP(ctx context.Context) (platform.SecurityKeyRotateResponse, error)
}

type MenuTreeProvider interface {
	Tree(ctx context.Context, userID int64) ([]platform.MenuNode, error)
}

type CompanySettingsProvider interface {
	Page(ctx context.Context, query platform.PageQuery, keyword string, status string) (platform.PageResponse[platform.CompanySetting], error)
	Detail(ctx context.Context, id int64) (platform.CompanySetting, error)
	Current(ctx context.Context) (*platform.CompanySetting, error)
	SaveCurrent(ctx context.Context, request platform.CompanySetting) (platform.CompanySetting, error)
	Create(ctx context.Context, request platform.CompanySetting) (platform.CompanySetting, error)
	Update(ctx context.Context, id int64, request platform.CompanySetting) (platform.CompanySetting, error)
	Delete(ctx context.Context, id int64) error
}

type DatabaseStatusProvider interface {
	Status(ctx context.Context) platform.DatabaseStatus
	Monitoring(ctx context.Context) platform.DatabaseMonitoring
}

type IoReportProvider interface {
	Page(ctx context.Context, query platform.PageQuery, filter platform.IoReportFilter) (platform.PageResponse[platform.IoReportResponse], error)
}

type InventoryReportProvider interface {
	Page(ctx context.Context, query platform.PageQuery, filter platform.InventoryReportFilter) (platform.PageResponse[platform.InventoryReportResponse], error)
	ExportExcel(ctx context.Context, keyword, warehouseName, category string) (platform.FileDownloadResponse, error)
}

type PendingInvoiceReceiptReportProvider interface {
	Page(ctx context.Context, query platform.PageQuery, filter platform.PendingInvoiceReceiptReportFilter) (platform.PageResponse[platform.PendingInvoiceReceiptReportResponse], error)
}

type GlobalSearcher interface {
	Search(ctx context.Context, userID int64, keyword string, limit int, moduleKeys []string) ([]platform.GlobalSearchResult, error)
}

type PermissionEntryProvider interface {
	Page(ctx context.Context, query platform.PageQuery, keyword string) (platform.PageResponse[platform.PermissionEntryResponse], error)
	Detail(ctx context.Context, id int64) (platform.PermissionEntryResponse, error)
}

type RoleSettingProvider interface {
	Page(ctx context.Context, query platform.PageQuery, keyword string, status string) (platform.PageResponse[platform.RoleSettingResponse], error)
	Detail(ctx context.Context, id int64) (platform.RoleSettingResponse, error)
	Create(ctx context.Context, operatorID int64, request platform.RoleSettingRequest) (platform.RoleSettingResponse, error)
	Update(ctx context.Context, operatorID int64, id int64, request platform.RoleSettingRequest) (platform.RoleSettingResponse, error)
	Delete(ctx context.Context, id int64) error
	Permissions(ctx context.Context, id int64) ([]platform.RolePermissionItem, error)
	SavePermissions(ctx context.Context, operatorID int64, id int64, permissions []platform.RolePermissionItem) error
	PermissionOptions(ctx context.Context) ([]platform.MenuNode, error)
	Templates(ctx context.Context) ([]platform.RoleTemplate, error)
}

type UserAccountAdminProvider interface {
	Page(ctx context.Context, query platform.PageQuery, keyword string, status string) (platform.PageResponse[platform.UserAccountAdminResponse], error)
	Detail(ctx context.Context, id int64) (platform.UserAccountAdminResponse, error)
	CheckLoginNameAvailability(ctx context.Context, loginName string, excludeUserID *int64) (platform.LoginNameAvailabilityResponse, error)
	Create(ctx context.Context, operatorID int64, request platform.UserAccountAdminRequest) (platform.UserAccountCreateResponse, error)
	Update(ctx context.Context, id int64, request platform.UserAccountAdminRequest) (platform.UserAccountAdminResponse, error)
	Delete(ctx context.Context, id int64) error
	Preferences(ctx context.Context, userID int64) (platform.UserAccountPreferencesPayload, error)
	SavePreferences(ctx context.Context, userID int64, request platform.UserAccountPreferencesPayload) (platform.UserAccountPreferencesPayload, error)
	Setup2FA(ctx context.Context, id int64) (platform.TotpSetupResponse, error)
	Enable2FA(ctx context.Context, id int64, request platform.TotpEnableRequest) (platform.UserAccountAdminResponse, error)
	Disable2FA(ctx context.Context, id int64) (platform.UserAccountAdminResponse, error)
}

type RefreshTokenAdminProvider interface {
	Page(ctx context.Context, query platform.PageQuery, keyword string) (platform.PageResponse[platform.RefreshTokenAdminResponse], error)
	Summary(ctx context.Context) (platform.RefreshTokenSessionSummaryResponse, error)
	Revoke(ctx context.Context, id int64) error
	RevokeAll(ctx context.Context) (int, error)
}

type ApiKeyAdminProvider interface {
	Page(ctx context.Context, operatorID int64, query platform.PageQuery, keyword string, userID *int64, status string, usageScope string) (platform.PageResponse[platform.ApiKeyResponse], error)
	Detail(ctx context.Context, operatorID int64, id int64) (platform.ApiKeyResponse, error)
	UserOptions(ctx context.Context, keyword string) ([]platform.ApiKeyUserOptionResponse, error)
	ResourceOptions(ctx context.Context) ([]platform.ApiKeyResourceOptionResponse, error)
	ActionOptions(ctx context.Context) ([]platform.ApiKeyActionOptionResponse, error)
	Generate(ctx context.Context, operatorID int64, userID int64, request platform.ApiKeyRequest) (platform.ApiKeyResponse, error)
	Revoke(ctx context.Context, operatorID int64, id int64) error
}

type PrintTemplateProvider interface {
	ListByBillType(ctx context.Context, billType string) ([]platform.PrintTemplateResponse, error)
	GetBillType(ctx context.Context, id int64) (string, error)
	Create(ctx context.Context, request platform.PrintTemplateRequest) (platform.PrintTemplateResponse, error)
	Update(ctx context.Context, id int64, request platform.PrintTemplateRequest) (platform.PrintTemplateResponse, error)
	UploadJSON(ctx context.Context, id int64, filename string, content []byte) (platform.PrintTemplateResponse, error)
	Delete(ctx context.Context, id int64) error
}

type MaterialCategoryProvider interface {
	Page(ctx context.Context, query platform.PageQuery, keyword string, status string) (platform.PageResponse[platform.MaterialCategoryResponse], error)
	Detail(ctx context.Context, id int64) (platform.MaterialCategoryResponse, error)
	Create(ctx context.Context, request platform.MaterialCategoryRequest) (platform.MaterialCategoryResponse, error)
	Update(ctx context.Context, id int64, request platform.MaterialCategoryRequest) (platform.MaterialCategoryResponse, error)
	Delete(ctx context.Context, id int64) error
	Options(ctx context.Context) ([]platform.MaterialCategoryOptionResponse, error)
}

type SupplierProvider interface {
	Page(ctx context.Context, query platform.PageQuery, keyword string, status string) (platform.PageResponse[platform.SupplierResponse], error)
	Detail(ctx context.Context, id int64) (platform.SupplierResponse, error)
	Create(ctx context.Context, request platform.SupplierRequest) (platform.SupplierResponse, error)
	Update(ctx context.Context, id int64, request platform.SupplierRequest) (platform.SupplierResponse, error)
	Delete(ctx context.Context, id int64) error
	Options(ctx context.Context) ([]platform.SupplierOptionResponse, error)
}

type CustomerProvider interface {
	Page(ctx context.Context, query platform.PageQuery, keyword string, status string) (platform.PageResponse[platform.CustomerResponse], error)
	Detail(ctx context.Context, id int64) (platform.CustomerResponse, error)
	Create(ctx context.Context, request platform.CustomerRequest) (platform.CustomerResponse, error)
	Update(ctx context.Context, id int64, request platform.CustomerRequest) (platform.CustomerResponse, error)
	Delete(ctx context.Context, id int64) error
	Options(ctx context.Context) ([]platform.CustomerOptionResponse, error)
}

type ProjectProvider interface {
	Page(ctx context.Context, query platform.PageQuery, keyword string, status string) (platform.PageResponse[platform.ProjectResponse], error)
	Detail(ctx context.Context, id int64) (platform.ProjectResponse, error)
	Create(ctx context.Context, request platform.ProjectRequest) (platform.ProjectResponse, error)
	Update(ctx context.Context, id int64, request platform.ProjectRequest) (platform.ProjectResponse, error)
	Delete(ctx context.Context, id int64) error
}

type CarrierProvider interface {
	Page(ctx context.Context, query platform.PageQuery, keyword string, status string) (platform.PageResponse[platform.CarrierResponse], error)
	Detail(ctx context.Context, id int64) (platform.CarrierResponse, error)
	Create(ctx context.Context, request platform.CarrierRequest) (platform.CarrierResponse, error)
	Update(ctx context.Context, id int64, request platform.CarrierRequest) (platform.CarrierResponse, error)
	Delete(ctx context.Context, id int64) error
	Options(ctx context.Context) ([]platform.CarrierOptionResponse, error)
}

type WarehouseProvider interface {
	Page(ctx context.Context, query platform.PageQuery, keyword string, warehouseType string, status string) (platform.PageResponse[platform.WarehouseResponse], error)
	Detail(ctx context.Context, id int64) (platform.WarehouseResponse, error)
	Create(ctx context.Context, request platform.WarehouseRequest) (platform.WarehouseResponse, error)
	Update(ctx context.Context, id int64, request platform.WarehouseRequest) (platform.WarehouseResponse, error)
	Delete(ctx context.Context, id int64) error
	Options(ctx context.Context) ([]platform.OptionResponse, error)
}

type RateLimitAdminProvider interface {
	ListRules(ctx context.Context) ([]map[string]any, error)
	UpdateRule(ctx context.Context, id int64, body map[string]any) error
}

type RouterServices struct {
	SetupInitializer            SetupInitializer
	Permission                  PermissionChecker
	Dashboard                   DashboardSummaryProvider
	Department                  DepartmentProvider
	OperationLog                OperationLogProvider
	GeneralSetting              GeneralSettingProvider
	UploadRule                  UploadRuleProvider
	SecurityKey                 SecurityKeyProvider
	Menu                        MenuTreeProvider
	Company                     CompanySettingsProvider
	Database                    DatabaseStatusProvider
	IoReport                    IoReportProvider
	InventoryReport             InventoryReportProvider
	PendingInvoiceReceiptReport PendingInvoiceReceiptReportProvider
	GlobalSearch                GlobalSearcher
	PermissionEntry             PermissionEntryProvider
	RoleSetting                 RoleSettingProvider
	UserAccount                 UserAccountAdminProvider
	RefreshToken                RefreshTokenAdminProvider
	ApiKey                      ApiKeyAdminProvider
	PrintTemplate               PrintTemplateProvider
	MaterialCategory            MaterialCategoryProvider
	Supplier                    SupplierProvider
	Customer                    CustomerProvider
	Project                     ProjectProvider
	Carrier                     CarrierProvider
	Warehouse                   WarehouseProvider
	RateLimit                   RateLimitAdminProvider
}

func NewRouter(
	cfg config.Config,
	logger *slog.Logger,
	healthChecker HealthChecker,
	setupStatusProvider SetupStatusProvider,
	captchaGenerator CaptchaGenerator,
	authProvider AuthProvider,
) http.Handler {
	return NewRouterWithServices(cfg, logger, healthChecker, setupStatusProvider, captchaGenerator, authProvider, RouterServices{})
}

func NewRouterWithServices(
	cfg config.Config,
	logger *slog.Logger,
	healthChecker HealthChecker,
	setupStatusProvider SetupStatusProvider,
	captchaGenerator CaptchaGenerator,
	authProvider AuthProvider,
	services RouterServices,
) http.Handler {
	gin.SetMode(gin.ReleaseMode)
	router := gin.New()
	router.Use(
		recoveryMiddleware(logger),
		traceMiddleware(),
		securityHeadersMiddleware(),
		corsMiddleware(),
		accessLogMiddleware(logger),
	)

	router.NoRoute(func(c *gin.Context) {
		writeFailure(c, http.StatusNotFound, codeNotFound, codeNotFound.Message)
	})

	apiGroup := router.Group("/api")
	api := apiHandler{
		cfg:                         cfg,
		healthChecker:               healthChecker,
		setupStatusProvider:         setupStatusProvider,
		captchaGenerator:            captchaGenerator,
		authProvider:                authProvider,
		setupInitializer:            services.SetupInitializer,
		permissionChecker:           services.Permission,
		dashboardProvider:           services.Dashboard,
		departmentProvider:          services.Department,
		operationLog:                services.OperationLog,
		generalSetting:              services.GeneralSetting,
		uploadRule:                  services.UploadRule,
		securityKey:                 services.SecurityKey,
		menuProvider:                services.Menu,
		companyProvider:             services.Company,
		databaseProvider:            services.Database,
		ioReport:                    services.IoReport,
		inventoryReport:             services.InventoryReport,
		pendingInvoiceReceiptReport: services.PendingInvoiceReceiptReport,
		globalSearcher:              services.GlobalSearch,
		permissionEntry:             services.PermissionEntry,
		roleSetting:                 services.RoleSetting,
		userAccount:                 services.UserAccount,
		refreshToken:                services.RefreshToken,
		apiKey:                      services.ApiKey,
		printTemplate:               services.PrintTemplate,
		materialCategory:            services.MaterialCategory,
		supplier:                    services.Supplier,
		customer:                    services.Customer,
		project:                     services.Project,
		carrier:                     services.Carrier,
		warehouse:                   services.Warehouse,
		rateLimit:                   services.RateLimit,
	}
	auth := authRequired(authProvider)
	apiGroup.GET("/health", api.health)
	apiGroup.GET("/auth/ping", api.authPing)
	apiGroup.GET("/auth/captcha", api.captcha)
	apiGroup.POST("/auth/login", api.login)
	apiGroup.POST("/auth/login-2fa", api.login2FA)
	apiGroup.POST("/auth/refresh", api.refresh)
	apiGroup.POST("/auth/logout", api.logout)
	apiGroup.GET("/account/security", auth, api.accountSecurityStatus)
	apiGroup.POST("/account/security/password", auth, api.accountSecurityChangePassword)
	apiGroup.POST("/account/security/2fa/setup", auth, api.accountSecuritySetup2FA)
	apiGroup.POST("/account/security/2fa/enable", auth, api.accountSecurityEnable2FA)
	apiGroup.POST("/account/security/2fa/disable", auth, api.accountSecurityDisable2FA)
	apiGroup.GET("/user-accounts/preference", auth, api.userAccountPreferences)
	apiGroup.PUT("/user-accounts/preference", auth, api.userAccountSavePreferences)
	apiGroup.GET("/user-accounts/login-name-availability", auth, permissionRequired(api.resolvePermissionChecker(), "user-account", "read"), api.userAccountLoginNameAvailability)
	apiGroup.GET("/user-accounts", auth, permissionRequired(api.resolvePermissionChecker(), "user-account", "read"), api.userAccountPage)
	apiGroup.GET("/user-accounts/:id", auth, permissionRequired(api.resolvePermissionChecker(), "user-account", "read"), api.userAccountDetail)
	apiGroup.POST("/user-accounts", auth, permissionRequired(api.resolvePermissionChecker(), "user-account", "create"), api.userAccountCreate)
	apiGroup.PUT("/user-accounts/:id", auth, permissionRequired(api.resolvePermissionChecker(), "user-account", "update"), api.userAccountUpdate)
	apiGroup.DELETE("/user-accounts/:id", auth, permissionRequired(api.resolvePermissionChecker(), "user-account", "delete"), api.userAccountDelete)
	apiGroup.POST("/user-accounts/:id/2fa/setup", auth, permissionRequired(api.resolvePermissionChecker(), "user-account", "update"), api.userAccountSetup2FA)
	apiGroup.POST("/user-accounts/:id/2fa/enable", auth, permissionRequired(api.resolvePermissionChecker(), "user-account", "update"), api.userAccountEnable2FA)
	apiGroup.POST("/user-accounts/:id/2fa/disable", auth, permissionRequired(api.resolvePermissionChecker(), "user-account", "update"), api.userAccountDisable2FA)
	apiGroup.GET("/auth/refresh-tokens/summary", auth, permissionRequired(api.resolvePermissionChecker(), "session", "read"), api.refreshTokenSummary)
	apiGroup.GET("/auth/refresh-tokens", auth, permissionRequired(api.resolvePermissionChecker(), "session", "read"), api.refreshTokenPage)
	apiGroup.POST("/auth/refresh-tokens/revoke-all", auth, permissionRequired(api.resolvePermissionChecker(), "session", "update"), api.refreshTokenRevokeAll)
	apiGroup.POST("/auth/refresh-tokens/:id/revoke", auth, permissionRequired(api.resolvePermissionChecker(), "session", "update"), api.refreshTokenRevoke)
	apiGroup.GET("/auth/api-keys/user-option", auth, permissionRequired(api.resolvePermissionChecker(), "api-key", "read"), api.apiKeyUserOptions)
	apiGroup.GET("/auth/api-keys/resource-option", auth, permissionRequired(api.resolvePermissionChecker(), "api-key", "read"), api.apiKeyResourceOptions)
	apiGroup.GET("/auth/api-keys/action-option", auth, permissionRequired(api.resolvePermissionChecker(), "api-key", "read"), api.apiKeyActionOptions)
	apiGroup.GET("/auth/api-keys", auth, permissionRequired(api.resolvePermissionChecker(), "api-key", "read"), api.apiKeyPage)
	apiGroup.GET("/auth/api-keys/:id", auth, permissionRequired(api.resolvePermissionChecker(), "api-key", "read"), api.apiKeyDetail)
	apiGroup.POST("/auth/api-keys", auth, permissionRequired(api.resolvePermissionChecker(), "api-key", "create"), api.apiKeyGenerate)
	apiGroup.POST("/auth/api-keys/:id/revoke", auth, permissionRequired(api.resolvePermissionChecker(), "api-key", "update"), api.apiKeyRevoke)
	apiGroup.GET("/setup/status", api.setupStatus)
	apiGroup.POST("/setup/initialize", api.setupInitialize)
	apiGroup.POST("/setup/admin/2fa/setup", api.setupAdminTotp)
	apiGroup.POST("/setup/admin", api.setupAdmin)
	apiGroup.POST("/setup/company", api.setupCompany)
	apiGroup.GET("/dashboard/summary", auth, permissionRequired(api.resolvePermissionChecker(), "dashboard", "read"), api.dashboardSummary)
	apiGroup.GET("/departments", auth, api.departmentPage)
	apiGroup.GET("/departments/option", auth, permissionRequired(api.resolvePermissionChecker(), "department", "read"), api.departmentOptions)
	apiGroup.GET("/departments/:id", auth, permissionRequired(api.resolvePermissionChecker(), "department", "read"), api.departmentDetail)
	apiGroup.POST("/departments", auth, permissionRequired(api.resolvePermissionChecker(), "department", "create"), api.departmentCreate)
	apiGroup.PUT("/departments/:id", auth, permissionRequired(api.resolvePermissionChecker(), "department", "update"), api.departmentUpdate)
	apiGroup.DELETE("/departments/:id", auth, permissionRequired(api.resolvePermissionChecker(), "department", "delete"), api.departmentDelete)
	apiGroup.GET("/system/menu/tree", auth, api.menuTree)
	apiGroup.GET("/permissions/catalog", auth, permissionRequired(api.resolvePermissionChecker(), "permission", "read"), api.permissionCatalog)
	apiGroup.GET("/permissions", auth, permissionRequired(api.resolvePermissionChecker(), "permission", "read"), api.permissionPage)
	apiGroup.GET("/permissions/:id", auth, permissionRequired(api.resolvePermissionChecker(), "permission", "read"), api.permissionDetail)
	apiGroup.GET("/company-settings/name", api.companyName)
	apiGroup.GET("/company-settings/current", auth, permissionRequired(api.resolvePermissionChecker(), "company-setting", "read"), api.companyCurrent)
	apiGroup.PUT("/company-settings/current", auth, permissionRequired(api.resolvePermissionChecker(), "company-setting", "update"), api.companySaveCurrent)
	apiGroup.GET("/company-settings", auth, permissionRequired(api.resolvePermissionChecker(), "company-setting", "read"), api.companyPage)
	apiGroup.GET("/company-settings/:id", auth, permissionRequired(api.resolvePermissionChecker(), "company-setting", "read"), api.companyDetail)
	apiGroup.POST("/company-settings", auth, permissionRequired(api.resolvePermissionChecker(), "company-setting", "create"), api.companyCreate)
	apiGroup.PUT("/company-settings/:id", auth, permissionRequired(api.resolvePermissionChecker(), "company-setting", "update"), api.companyUpdate)
	apiGroup.DELETE("/company-settings/:id", auth, permissionRequired(api.resolvePermissionChecker(), "company-setting", "delete"), api.companyDelete)
	apiGroup.GET("/role-settings", auth, permissionRequired(api.resolvePermissionChecker(), "role", "read"), api.roleSettingPage)
	apiGroup.GET("/role-settings/permission-option", auth, permissionRequired(api.resolvePermissionChecker(), "role", "manage_permissions"), api.roleSettingPermissionOptions)
	apiGroup.GET("/role-settings/templates", auth, permissionRequired(api.resolvePermissionChecker(), "role", "manage_permissions"), api.roleSettingTemplates)
	apiGroup.GET("/role-settings/:id", auth, permissionRequired(api.resolvePermissionChecker(), "role", "read"), api.roleSettingDetail)
	apiGroup.POST("/role-settings", auth, permissionRequired(api.resolvePermissionChecker(), "role", "create"), api.roleSettingCreate)
	apiGroup.PUT("/role-settings/:id", auth, permissionRequired(api.resolvePermissionChecker(), "role", "update"), api.roleSettingUpdate)
	apiGroup.DELETE("/role-settings/:id", auth, permissionRequired(api.resolvePermissionChecker(), "role", "delete"), api.roleSettingDelete)
	apiGroup.GET("/role-settings/:id/permission", auth, permissionRequired(api.resolvePermissionChecker(), "role", "manage_permissions"), api.roleSettingPermissions)
	apiGroup.PUT("/role-settings/:id/permission", auth, permissionRequired(api.resolvePermissionChecker(), "role", "manage_permissions"), api.roleSettingSavePermissions)
	apiGroup.GET("/operation-logs", auth, permissionRequired(api.resolvePermissionChecker(), "operation-log", "read"), api.operationLogPage)
	apiGroup.GET("/io-report", auth, permissionRequired(api.resolvePermissionChecker(), "io-report", "read"), api.ioReportPage)
	apiGroup.GET("/inventory-report", auth, permissionRequired(api.resolvePermissionChecker(), "inventory-report", "read"), api.inventoryReportPage)
	apiGroup.POST("/inventory-report/export", auth, permissionRequired(api.resolvePermissionChecker(), "inventory-report", "export"), api.inventoryReportExport)
	apiGroup.GET("/pending-invoice-receipt-report", auth, permissionRequired(api.resolvePermissionChecker(), "pending-invoice-receipt-report", "read"), api.pendingInvoiceReceiptReportPage)
	apiGroup.GET("/general-settings/display-switch", auth, api.generalSettingDisplaySwitches)
	apiGroup.GET("/general-settings/client-setting", auth, api.generalSettingClientSettings)
	apiGroup.GET("/general-settings/statement-generator-rule", auth, api.generalSettingStatementGeneratorRules)
	apiGroup.POST("/general-settings/number-rule/next", auth, api.generalSettingNextNumber)
	apiGroup.GET("/general-settings/upload-rule", auth, api.uploadRuleDetail)
	apiGroup.PUT("/general-settings/upload-rule", auth, permissionRequired(api.resolvePermissionChecker(), "general-setting", "update"), api.uploadRuleUpdate)
	apiGroup.GET("/general-settings", auth, permissionRequired(api.resolvePermissionChecker(), "general-setting", "read"), api.generalSettingPage)
	apiGroup.GET("/general-settings/:id", auth, permissionRequired(api.resolvePermissionChecker(), "general-setting", "read"), api.generalSettingDetail)
	apiGroup.POST("/general-settings", auth, permissionRequired(api.resolvePermissionChecker(), "general-setting", "update"), api.generalSettingCreate)
	apiGroup.PUT("/general-settings/:id", auth, permissionRequired(api.resolvePermissionChecker(), "general-setting", "update"), api.generalSettingUpdate)
	apiGroup.DELETE("/general-settings/:id", auth, permissionRequired(api.resolvePermissionChecker(), "general-setting", "update"), api.generalSettingDelete)
	apiGroup.GET("/material-categories", auth, permissionRequired(api.resolvePermissionChecker(), "material", "read"), api.materialCategoryPage)
	apiGroup.GET("/material-categories/option", auth, permissionRequired(api.resolvePermissionChecker(), "material", "read"), api.materialCategoryOptions)
	apiGroup.GET("/material-categories/:id", auth, permissionRequired(api.resolvePermissionChecker(), "material", "read"), api.materialCategoryDetail)
	apiGroup.POST("/material-categories", auth, permissionRequired(api.resolvePermissionChecker(), "material", "create"), api.materialCategoryCreate)
	apiGroup.PUT("/material-categories/:id", auth, permissionRequired(api.resolvePermissionChecker(), "material", "update"), api.materialCategoryUpdate)
	apiGroup.DELETE("/material-categories/:id", auth, permissionRequired(api.resolvePermissionChecker(), "material", "delete"), api.materialCategoryDelete)
	apiGroup.GET("/suppliers/option", auth, permissionRequired(api.resolvePermissionChecker(), "supplier", "read"), api.supplierOptions)
	apiGroup.GET("/suppliers", auth, permissionRequired(api.resolvePermissionChecker(), "supplier", "read"), api.supplierPage)
	apiGroup.GET("/suppliers/:id", auth, permissionRequired(api.resolvePermissionChecker(), "supplier", "read"), api.supplierDetail)
	apiGroup.POST("/suppliers", auth, permissionRequired(api.resolvePermissionChecker(), "supplier", "create"), api.supplierCreate)
	apiGroup.PUT("/suppliers/:id", auth, permissionRequired(api.resolvePermissionChecker(), "supplier", "update"), api.supplierUpdate)
	apiGroup.DELETE("/suppliers/:id", auth, permissionRequired(api.resolvePermissionChecker(), "supplier", "delete"), api.supplierDelete)
	apiGroup.GET("/customers/option", auth, permissionRequired(api.resolvePermissionChecker(), "customer", "read"), api.customerOptions)
	apiGroup.GET("/customers", auth, permissionRequired(api.resolvePermissionChecker(), "customer", "read"), api.customerPage)
	apiGroup.GET("/customers/:id", auth, permissionRequired(api.resolvePermissionChecker(), "customer", "read"), api.customerDetail)
	apiGroup.POST("/customers", auth, permissionRequired(api.resolvePermissionChecker(), "customer", "create"), api.customerCreate)
	apiGroup.PUT("/customers/:id", auth, permissionRequired(api.resolvePermissionChecker(), "customer", "update"), api.customerUpdate)
	apiGroup.DELETE("/customers/:id", auth, permissionRequired(api.resolvePermissionChecker(), "customer", "delete"), api.customerDelete)
	apiGroup.GET("/projects", auth, permissionRequired(api.resolvePermissionChecker(), "project", "read"), api.projectPage)
	apiGroup.GET("/projects/:id", auth, permissionRequired(api.resolvePermissionChecker(), "project", "read"), api.projectDetail)
	apiGroup.POST("/projects", auth, permissionRequired(api.resolvePermissionChecker(), "project", "create"), api.projectCreate)
	apiGroup.PUT("/projects/:id", auth, permissionRequired(api.resolvePermissionChecker(), "project", "update"), api.projectUpdate)
	apiGroup.DELETE("/projects/:id", auth, permissionRequired(api.resolvePermissionChecker(), "project", "delete"), api.projectDelete)
	apiGroup.GET("/carriers/option", auth, permissionRequired(api.resolvePermissionChecker(), "carrier", "read"), api.carrierOptions)
	apiGroup.GET("/carriers", auth, permissionRequired(api.resolvePermissionChecker(), "carrier", "read"), api.carrierPage)
	apiGroup.GET("/carriers/:id", auth, permissionRequired(api.resolvePermissionChecker(), "carrier", "read"), api.carrierDetail)
	apiGroup.POST("/carriers", auth, permissionRequired(api.resolvePermissionChecker(), "carrier", "create"), api.carrierCreate)
	apiGroup.PUT("/carriers/:id", auth, permissionRequired(api.resolvePermissionChecker(), "carrier", "update"), api.carrierUpdate)
	apiGroup.DELETE("/carriers/:id", auth, permissionRequired(api.resolvePermissionChecker(), "carrier", "delete"), api.carrierDelete)
	apiGroup.GET("/warehouses/option", auth, permissionRequired(api.resolvePermissionChecker(), "warehouse", "read"), api.warehouseOptions)
	apiGroup.GET("/warehouses", auth, permissionRequired(api.resolvePermissionChecker(), "warehouse", "read"), api.warehousePage)
	apiGroup.GET("/warehouses/:id", auth, permissionRequired(api.resolvePermissionChecker(), "warehouse", "read"), api.warehouseDetail)
	apiGroup.POST("/warehouses", auth, permissionRequired(api.resolvePermissionChecker(), "warehouse", "create"), api.warehouseCreate)
	apiGroup.PUT("/warehouses/:id", auth, permissionRequired(api.resolvePermissionChecker(), "warehouse", "update"), api.warehouseUpdate)
	apiGroup.DELETE("/warehouses/:id", auth, permissionRequired(api.resolvePermissionChecker(), "warehouse", "delete"), api.warehouseDelete)
	apiGroup.GET("/admin/rate-limit/rules", auth, permissionRequired(api.resolvePermissionChecker(), "general-setting", "read"), api.rateLimitRuleList)
	apiGroup.PUT("/admin/rate-limit/rules/:id", auth, permissionRequired(api.resolvePermissionChecker(), "general-setting", "update"), api.rateLimitRuleUpdate)
	apiGroup.GET("/print-templates", auth, api.printTemplateList)
	apiGroup.POST("/print-templates", auth, permissionRequired(api.resolvePermissionChecker(), "print-template", "create"), api.printTemplateCreate)
	apiGroup.PUT("/print-templates/:id", auth, permissionRequired(api.resolvePermissionChecker(), "print-template", "update"), api.printTemplateUpdate)
	apiGroup.POST("/print-templates/:id/upload-json", auth, permissionRequired(api.resolvePermissionChecker(), "print-template", "update"), api.printTemplateUploadJSON)
	apiGroup.DELETE("/print-templates/:id", auth, permissionRequired(api.resolvePermissionChecker(), "print-template", "delete"), api.printTemplateDelete)
	apiGroup.GET("/system/security-keys", auth, permissionRequired(api.resolvePermissionChecker(), "security-key", "read"), api.securityKeyOverview)
	apiGroup.POST("/system/security-keys/jwt/rotate", auth, permissionRequired(api.resolvePermissionChecker(), "security-key", "update"), api.securityKeyRotateJWT)
	apiGroup.POST("/system/security-keys/totp/rotate", auth, permissionRequired(api.resolvePermissionChecker(), "security-key", "update"), api.securityKeyRotateTOTP)
	apiGroup.GET("/system/databases/status", auth, permissionRequired(api.resolvePermissionChecker(), "database", "read"), api.databaseStatus)
	apiGroup.GET("/system/databases/monitoring", auth, permissionRequired(api.resolvePermissionChecker(), "database", "read"), api.databaseMonitoring)
	apiGroup.GET("/global-search", auth, api.globalSearch)
	apiGroup.GET("/meta/code", api.metaCode)
	apiGroup.GET("/api/meta/code", api.metaCode)

	return router
}

func corsMiddleware() gin.HandlerFunc {
	allowedOrigins := map[string]struct{}{
		"http://localhost:3100": {},
		"http://127.0.0.1:3100": {},
	}
	return func(c *gin.Context) {
		origin := c.GetHeader("Origin")
		if _, ok := allowedOrigins[origin]; ok {
			c.Header("Access-Control-Allow-Origin", origin)
			c.Header("Access-Control-Allow-Credentials", "true")
			c.Header("Vary", "Origin")
		}
		c.Header("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Origin,Content-Type,Accept,Authorization,X-Trace-Id,X-TOTP-Code,Idempotency-Key")
		c.Header("Access-Control-Expose-Headers", "X-Trace-Id,X-RateLimit-Limit,X-RateLimit-Remaining,Retry-After")

		if c.Request.Method == http.MethodOptions {
			c.AbortWithStatus(http.StatusNoContent)
			return
		}
		c.Next()
	}
}
