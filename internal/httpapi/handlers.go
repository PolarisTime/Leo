package httpapi

import (
	"context"
	"errors"
	"io"
	"mime"
	"net/http"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/leo-erp/leo/internal/config"
	"github.com/leo-erp/leo/internal/platform"
)

var pageSortByPattern = regexp.MustCompile(`^[A-Za-z][A-Za-z0-9_]*$`)

type apiHandler struct {
	cfg                         config.Config
	healthChecker               HealthChecker
	setupStatusProvider         SetupStatusProvider
	captchaGenerator            CaptchaGenerator
	authProvider                AuthProvider
	setupInitializer            SetupInitializer
	permissionChecker           PermissionChecker
	dashboardProvider           DashboardSummaryProvider
	departmentProvider          DepartmentProvider
	operationLog                OperationLogProvider
	generalSetting              GeneralSettingProvider
	uploadRule                  UploadRuleProvider
	securityKey                 SecurityKeyProvider
	menuProvider                MenuTreeProvider
	companyProvider             CompanySettingsProvider
	databaseProvider            DatabaseStatusProvider
	ioReport                    IoReportProvider
	inventoryReport             InventoryReportProvider
	pendingInvoiceReceiptReport PendingInvoiceReceiptReportProvider
	globalSearcher              GlobalSearcher
	permissionEntry             PermissionEntryProvider
	roleSetting                 RoleSettingProvider
	userAccount                 UserAccountAdminProvider
	refreshToken                RefreshTokenAdminProvider
	apiKey                      ApiKeyAdminProvider
	printTemplate               PrintTemplateProvider
	materialCategory            MaterialCategoryProvider
	supplier                    SupplierProvider
	customer                    CustomerProvider
	project                     ProjectProvider
	carrier                     CarrierProvider
	warehouse                   WarehouseProvider
	rateLimit                   RateLimitAdminProvider
}

type healthResponse struct {
	Status    string              `json:"status"`
	App       string              `json:"app"`
	TraceID   string              `json:"traceId"`
	Timestamp string              `json:"timestamp"`
	DB        healthCheckResponse `json:"db"`
	Redis     healthCheckResponse `json:"redis"`
	Disk      healthCheckResponse `json:"disk"`
}

type healthCheckResponse struct {
	Status  string `json:"status"`
	FreeGB  uint64 `json:"freeGb"`
	TotalGB uint64 `json:"totalGb"`
}

type setupStatusResponse struct {
	SetupRequired     bool `json:"setupRequired"`
	AdminConfigured   bool `json:"adminConfigured"`
	CompanyConfigured bool `json:"companyConfigured"`
}

type metaCodeResponse struct {
	ErrorCodes     []ErrorCode       `json:"errorCodes"`
	ResourceLabels map[string]string `json:"resourceLabels"`
	ActionLabels   map[string]string `json:"actionLabels"`
}

type captchaResponse struct {
	CaptchaID    string `json:"captchaId"`
	CaptchaImage string `json:"captchaImage"`
	Required     bool   `json:"required"`
}

type loginRequest struct {
	LoginName   string `json:"loginName"`
	Password    string `json:"password"`
	CaptchaID   string `json:"captchaId"`
	CaptchaCode string `json:"captchaCode"`
}

type login2FARequest struct {
	TempToken string `json:"tempToken"`
	TotpCode  string `json:"totpCode"`
}

type changePasswordRequest struct {
	CurrentPassword string `json:"currentPassword"`
	NewPassword     string `json:"newPassword"`
}

type totpCodeRequest struct {
	TotpCode string `json:"totpCode"`
}

type totpSetupResponse struct {
	QrCodeBase64 string `json:"qrCodeBase64"`
	Secret       string `json:"secret"`
}

type refreshRequest struct {
	RefreshToken string `json:"refreshToken"`
}

type logoutRequest struct {
	RefreshToken string `json:"refreshToken"`
}

type initialSetupTotpSetupRequest struct {
	LoginName string `json:"loginName"`
}

type initialSetupAdminSubmitRequest struct {
	Admin      *initialSetupAdminRequest `json:"admin"`
	TotpSecret string                    `json:"totpSecret"`
	TotpCode   string                    `json:"totpCode"`
}

type initialSetupAdminRequest struct {
	LoginName string `json:"loginName"`
	Password  string `json:"password"`
	UserName  string `json:"userName"`
	Mobile    string `json:"mobile"`
}

type initialSetupCompanyRequest struct {
	CompanyName string   `json:"companyName"`
	TaxNo       string   `json:"taxNo"`
	BankName    string   `json:"bankName"`
	BankAccount string   `json:"bankAccount"`
	TaxRate     *float64 `json:"taxRate"`
	Remark      string   `json:"remark"`
}

type initialSetupSubmitRequest struct {
	Admin   *initialSetupAdminSubmitRequest `json:"admin"`
	Company *initialSetupCompanyRequest     `json:"company"`
}

func (h apiHandler) health(c *gin.Context) {
	ctx, cancel := context.WithTimeout(c.Request.Context(), h.cfg.Database.ValidationTimeout)
	defer cancel()
	checks := h.resolveHealthChecker().Check(ctx)
	db := healthCheckResponse(checks.DB)
	redis := healthCheckResponse(checks.Redis)
	disk := healthCheckResponse(checks.Disk)
	status := "UP"
	if db.Status != "UP" || redis.Status != "UP" || disk.Status != "UP" {
		status = "DEGRADED"
	}
	writeSuccess(c, http.StatusOK, "", healthResponse{
		Status:    status,
		App:       h.cfg.AppName,
		TraceID:   traceID(c),
		Timestamp: nowText(),
		DB:        db,
		Redis:     redis,
		Disk:      disk,
	})
}

func (h apiHandler) authPing(c *gin.Context) {
	writeSuccess(c, http.StatusOK, "认证模块可用", "pong")
}

func (h apiHandler) captcha(c *gin.Context) {
	ctx, cancel := context.WithTimeout(c.Request.Context(), h.cfg.Redis.Timeout+h.cfg.Database.ValidationTimeout)
	defer cancel()
	captcha, err := h.resolveCaptchaGenerator().Generate(ctx)
	if err != nil {
		writeFailure(c, http.StatusInternalServerError, codeInternalError, "系统异常")
		return
	}
	writeSuccess(c, http.StatusOK, "", captchaResponse{
		CaptchaID:    captcha.CaptchaID,
		CaptchaImage: captcha.CaptchaImage,
		Required:     captcha.Required,
	})
}

func (h apiHandler) login(c *gin.Context) {
	var req loginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveAuthProvider().Login(c.Request.Context(), platform.LoginRequest{
		LoginName:   req.LoginName,
		Password:    req.Password,
		CaptchaID:   req.CaptchaID,
		CaptchaCode: req.CaptchaCode,
	}, clientIP(c), c.GetHeader("User-Agent"), h.resolveCaptchaVerifier())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	if result.RefreshToken != nil && strings.TrimSpace(*result.RefreshToken) != "" {
		writeRefreshCookie(c, *result.RefreshToken, h.cfg.JWT.RefreshExpiration, h.cfg.JWT.RefreshCookieSecure)
		result.RefreshToken = nil
	}
	writeSuccess(c, http.StatusOK, "登录成功", result)
}

func (h apiHandler) login2FA(c *gin.Context) {
	var req login2FARequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveAuthProvider().Login2FA(c.Request.Context(), platform.Login2FARequest{
		TempToken: req.TempToken,
		TotpCode:  req.TotpCode,
	}, clientIP(c), c.GetHeader("User-Agent"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeRefreshCookie(c, result.RefreshToken, h.cfg.JWT.RefreshExpiration, h.cfg.JWT.RefreshCookieSecure)
	result.RefreshToken = ""
	writeSuccess(c, http.StatusOK, "登录成功", result)
}

func (h apiHandler) refresh(c *gin.Context) {
	var req refreshRequest
	if c.Request.Body != nil && c.Request.ContentLength != 0 {
		if err := c.ShouldBindJSON(&req); err != nil {
			writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
			return
		}
	}
	refreshToken := resolveRefreshToken(c, req.RefreshToken)
	if strings.TrimSpace(refreshToken) == "" {
		clearRefreshCookie(c, h.cfg.JWT.RefreshCookieSecure)
		writeSuccess(c, http.StatusOK, "未登录", nil)
		return
	}
	result, err := h.resolveAuthProvider().Refresh(c.Request.Context(), refreshToken, clientIP(c), c.GetHeader("User-Agent"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeRefreshCookie(c, result.RefreshToken, h.cfg.JWT.RefreshExpiration, h.cfg.JWT.RefreshCookieSecure)
	result.RefreshToken = ""
	writeSuccess(c, http.StatusOK, "刷新成功", result)
}

func (h apiHandler) logout(c *gin.Context) {
	var req logoutRequest
	if c.Request.Body != nil && c.Request.ContentLength != 0 {
		if err := c.ShouldBindJSON(&req); err != nil {
			writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
			return
		}
	}
	if err := h.resolveAuthProvider().Logout(c.Request.Context(), resolveRefreshToken(c, req.RefreshToken)); err != nil {
		writeAuthError(c, err)
		return
	}
	clearRefreshCookie(c, h.cfg.JWT.RefreshCookieSecure)
	writeSuccess(c, http.StatusOK, "退出成功", nil)
}

func (h apiHandler) accountSecurityStatus(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	result, err := h.resolveAuthProvider().CurrentUserSecurityStatus(c.Request.Context(), principal.UserID)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) accountSecurityChangePassword(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	var req changePasswordRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	if err := h.resolveAccountSecurityWriter().ChangeCurrentUserPassword(c.Request.Context(), principal.UserID, req.CurrentPassword, req.NewPassword); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "密码修改成功", nil)
}

func (h apiHandler) accountSecuritySetup2FA(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	qrCodeBase64, secret, err := h.resolveAccountSecurityWriter().SetupCurrentUser2FA(c.Request.Context(), principal.UserID)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "密钥生成成功", totpSetupResponse{
		QrCodeBase64: qrCodeBase64,
		Secret:       secret,
	})
}

func (h apiHandler) accountSecurityEnable2FA(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	var req totpCodeRequest
	if c.Request.Body != nil && c.Request.ContentLength != 0 {
		if err := c.ShouldBindJSON(&req); err != nil {
			writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
			return
		}
	}
	result, err := h.resolveAccountSecurityWriter().EnableCurrentUser2FA(c.Request.Context(), principal.UserID, resolveTotpCode(c, req.TotpCode))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "2FA已启用", result)
}

func (h apiHandler) accountSecurityDisable2FA(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	var req totpCodeRequest
	if c.Request.Body != nil && c.Request.ContentLength != 0 {
		if err := c.ShouldBindJSON(&req); err != nil {
			writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
			return
		}
	}
	result, err := h.resolveAccountSecurityWriter().DisableCurrentUser2FA(c.Request.Context(), principal.UserID, resolveTotpCode(c, req.TotpCode))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "2FA已禁用", result)
}

func (h apiHandler) setupStatus(c *gin.Context) {
	ctx, cancel := context.WithTimeout(c.Request.Context(), h.cfg.Database.ValidationTimeout)
	defer cancel()
	status, err := h.resolveSetupStatusProvider().Status(ctx)
	if err != nil {
		writeFailure(c, http.StatusInternalServerError, codeInternalError, "系统异常")
		return
	}
	writeSuccess(c, http.StatusOK, "获取初始化状态成功", setupStatusResponse{
		SetupRequired:     status.SetupRequired,
		AdminConfigured:   status.AdminConfigured,
		CompanyConfigured: status.CompanyConfigured,
	})
}

func (h apiHandler) setupInitialize(c *gin.Context) {
	var req initialSetupSubmitRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveSetupInitializer().Initialize(c.Request.Context(), platform.InitialSetupSubmitRequest{
		Admin:   toPlatformInitialSetupAdminSubmitRequest(req.Admin),
		Company: toPlatformInitialSetupCompanyRequest(req.Company),
	})
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "系统首次初始化完成", result)
}

func (h apiHandler) setupAdminTotp(c *gin.Context) {
	var req initialSetupTotpSetupRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveSetupInitializer().SetupAdminTotp(c.Request.Context(), platform.InitialSetupTotpSetupRequest{
		LoginName: req.LoginName,
	})
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "管理员 2FA 已生成", result)
}

func (h apiHandler) setupAdmin(c *gin.Context) {
	var req initialSetupAdminSubmitRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	platformReq := toPlatformInitialSetupAdminSubmitRequest(&req)
	if platformReq == nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请填写管理员账号信息")
		return
	}
	result, err := h.resolveSetupInitializer().ConfigureAdmin(c.Request.Context(), *platformReq)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "管理员账号初始化完成", result)
}

func (h apiHandler) setupCompany(c *gin.Context) {
	var req initialSetupCompanyRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveSetupInitializer().ConfigureCompany(c.Request.Context(), platform.InitialSetupCompanyRequest{
		CompanyName: req.CompanyName,
		TaxNo:       req.TaxNo,
		BankName:    req.BankName,
		BankAccount: req.BankAccount,
		TaxRate:     req.TaxRate,
		Remark:      req.Remark,
	})
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "公司主体初始化完成", result)
}

func toPlatformInitialSetupAdminSubmitRequest(req *initialSetupAdminSubmitRequest) *platform.InitialSetupAdminSubmitRequest {
	if req == nil {
		return nil
	}
	return &platform.InitialSetupAdminSubmitRequest{
		Admin:      toPlatformInitialSetupAdminRequest(req.Admin),
		TotpSecret: req.TotpSecret,
		TotpCode:   req.TotpCode,
	}
}

func toPlatformInitialSetupAdminRequest(req *initialSetupAdminRequest) *platform.InitialSetupAdminRequest {
	if req == nil {
		return nil
	}
	return &platform.InitialSetupAdminRequest{
		LoginName: req.LoginName,
		Password:  req.Password,
		UserName:  req.UserName,
		Mobile:    req.Mobile,
	}
}

func toPlatformInitialSetupCompanyRequest(req *initialSetupCompanyRequest) *platform.InitialSetupCompanyRequest {
	if req == nil {
		return nil
	}
	return &platform.InitialSetupCompanyRequest{
		CompanyName: req.CompanyName,
		TaxNo:       req.TaxNo,
		BankName:    req.BankName,
		BankAccount: req.BankAccount,
		TaxRate:     req.TaxRate,
		Remark:      req.Remark,
	}
}

func (h apiHandler) dashboardSummary(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	result, err := h.resolveDashboardProvider().Summary(c.Request.Context(), principal.UserID)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) menuTree(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	result, err := h.resolveMenuProvider().Tree(c.Request.Context(), principal.UserID)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) userAccountPage(c *gin.Context) {
	result, err := h.resolveUserAccountProvider().Page(c.Request.Context(), pageQuery(c), c.Query("keyword"), c.Query("status"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) userAccountDetail(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolveUserAccountProvider().Detail(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) userAccountLoginNameAvailability(c *gin.Context) {
	var excludeUserID *int64
	if raw := strings.TrimSpace(c.Query("excludeUserId")); raw != "" {
		value, err := strconv.ParseInt(raw, 10, 64)
		if err != nil || value <= 0 {
			writeFailure(c, http.StatusBadRequest, codeValidationError, "excludeUserId必须为正整数")
			return
		}
		excludeUserID = &value
	}
	result, err := h.resolveUserAccountProvider().CheckLoginNameAvailability(c.Request.Context(), c.Query("loginName"), excludeUserID)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) userAccountCreate(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	var req platform.UserAccountAdminRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveUserAccountProvider().Create(c.Request.Context(), principal.UserID, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "创建成功", result)
}

func (h apiHandler) userAccountUpdate(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	var req platform.UserAccountAdminRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveUserAccountProvider().Update(c.Request.Context(), id, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "更新成功", result)
}

func (h apiHandler) userAccountDelete(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	if err := h.resolveUserAccountProvider().Delete(c.Request.Context(), id); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "删除成功", nil)
}

func (h apiHandler) userAccountPreferences(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	result, err := h.resolveUserAccountProvider().Preferences(c.Request.Context(), principal.UserID)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) userAccountSavePreferences(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	var req platform.UserAccountPreferencesPayload
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveUserAccountProvider().SavePreferences(c.Request.Context(), principal.UserID, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "保存成功", result)
}

func (h apiHandler) userAccountSetup2FA(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolveUserAccountProvider().Setup2FA(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "密钥生成成功", result)
}

func (h apiHandler) userAccountEnable2FA(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	var req platform.TotpEnableRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveUserAccountProvider().Enable2FA(c.Request.Context(), id, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "2FA已启用", result)
}

func (h apiHandler) userAccountDisable2FA(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolveUserAccountProvider().Disable2FA(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "2FA已禁用", result)
}

func (h apiHandler) refreshTokenPage(c *gin.Context) {
	result, err := h.resolveRefreshTokenProvider().Page(c.Request.Context(), pageQuery(c), c.Query("keyword"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) refreshTokenSummary(c *gin.Context) {
	result, err := h.resolveRefreshTokenProvider().Summary(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) refreshTokenRevoke(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	if err := h.resolveRefreshTokenProvider().Revoke(c.Request.Context(), id); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "已禁用", nil)
}

func (h apiHandler) refreshTokenRevokeAll(c *gin.Context) {
	count, err := h.resolveRefreshTokenProvider().RevokeAll(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "已禁用 "+strconv.Itoa(count)+" 个令牌", count)
}

func (h apiHandler) apiKeyPage(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	userID, ok := optionalInt64Query(c, "userId")
	if !ok {
		return
	}
	result, err := h.resolveApiKeyProvider().Page(c.Request.Context(), principal.UserID, pageQuery(c), c.Query("keyword"), userID, c.Query("status"), c.Query("usageScope"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) apiKeyDetail(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolveApiKeyProvider().Detail(c.Request.Context(), principal.UserID, id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) apiKeyUserOptions(c *gin.Context) {
	result, err := h.resolveApiKeyProvider().UserOptions(c.Request.Context(), c.Query("keyword"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) apiKeyResourceOptions(c *gin.Context) {
	result, err := h.resolveApiKeyProvider().ResourceOptions(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) apiKeyActionOptions(c *gin.Context) {
	result, err := h.resolveApiKeyProvider().ActionOptions(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) apiKeyGenerate(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	userIDRaw := strings.TrimSpace(c.Query("userId"))
	userID, err := strconv.ParseInt(userIDRaw, 10, 64)
	if err != nil || userID <= 0 {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "userId必须为正整数")
		return
	}
	var req platform.ApiKeyRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveApiKeyProvider().Generate(c.Request.Context(), principal.UserID, userID, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "API Key 已生成", result)
}

func (h apiHandler) apiKeyRevoke(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	id, ok := pathID(c)
	if !ok {
		return
	}
	if err := h.resolveApiKeyProvider().Revoke(c.Request.Context(), principal.UserID, id); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "已禁用", nil)
}

func (h apiHandler) permissionCatalog(c *gin.Context) {
	writeSuccess(c, http.StatusOK, "", platform.PermissionCatalog())
}

func (h apiHandler) permissionPage(c *gin.Context) {
	result, err := h.resolvePermissionEntryProvider().Page(c.Request.Context(), pageQuery(c), c.Query("keyword"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) permissionDetail(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolvePermissionEntryProvider().Detail(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) departmentPage(c *gin.Context) {
	result, err := h.resolveDepartmentProvider().Page(c.Request.Context(), pageQuery(c), c.Query("keyword"), c.Query("status"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) departmentOptions(c *gin.Context) {
	result, err := h.resolveDepartmentProvider().Options(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) departmentDetail(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolveDepartmentProvider().Detail(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) departmentCreate(c *gin.Context) {
	var req platform.DepartmentRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveDepartmentProvider().Create(c.Request.Context(), req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "创建成功", result)
}

func (h apiHandler) departmentUpdate(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	var req platform.DepartmentRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveDepartmentProvider().Update(c.Request.Context(), id, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "更新成功", result)
}

func (h apiHandler) departmentDelete(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	if err := h.resolveDepartmentProvider().Delete(c.Request.Context(), id); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "删除成功", nil)
}

func (h apiHandler) operationLogPage(c *gin.Context) {
	recordID, ok := optionalInt64Query(c, "recordId")
	if !ok {
		return
	}
	result, err := h.resolveOperationLogProvider().Page(c.Request.Context(), pageQuery(c), platform.OperationLogFilter{
		Keyword:      c.Query("keyword"),
		ModuleName:   c.Query("moduleName"),
		ActionType:   c.Query("actionType"),
		ResultStatus: c.Query("resultStatus"),
		StartTime:    c.Query("startTime"),
		EndTime:      c.Query("endTime"),
		RecordID:     recordID,
		AuthType:     c.Query("authType"),
	})
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) ioReportPage(c *gin.Context) {
	query, ok := strictPageQueryWithDirectionParam(c, "sortDirection", map[string]struct{}{
		"businessDate":  {},
		"businessType":  {},
		"sourceNo":      {},
		"materialCode":  {},
		"warehouseName": {},
	})
	if !ok {
		return
	}
	result, err := h.resolveIoReportProvider().Page(c.Request.Context(), query, platform.IoReportFilter{
		Keyword:      c.Query("keyword"),
		BusinessType: c.Query("businessType"),
		StartDate:    c.Query("startDate"),
		EndDate:      c.Query("endDate"),
	})
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) inventoryReportPage(c *gin.Context) {
	query, ok := strictPageQueryWithDirectionParam(c, "sortDirection", map[string]struct{}{
		"brand":         {},
		"category":      {},
		"warehouseName": {},
		"quantity":      {},
		"weightTon":     {},
	})
	if !ok {
		return
	}
	result, err := h.resolveInventoryReportProvider().Page(c.Request.Context(), query, platform.InventoryReportFilter{
		Keyword:       c.Query("keyword"),
		WarehouseName: c.Query("warehouseName"),
		Category:      c.Query("category"),
	})
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

type inventoryReportExportRequest struct {
	Keyword       string `json:"keyword"`
	WarehouseName string `json:"warehouseName"`
	Category      string `json:"category"`
}

func (h apiHandler) inventoryReportExport(c *gin.Context) {
	var req inventoryReportExportRequest
	if c.Request.Body != nil && c.Request.ContentLength != 0 {
		if err := c.ShouldBindJSON(&req); err != nil && !errors.Is(err, io.EOF) {
			writeAuthError(c, platform.NewAuthError(platform.AuthErrorValidation, "请求参数格式不正确"))
			return
		}
	}
	file, err := h.resolveInventoryReportProvider().ExportExcel(c.Request.Context(), req.Keyword, req.WarehouseName, req.Category)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeFileDownload(c, file)
}

func (h apiHandler) pendingInvoiceReceiptReportPage(c *gin.Context) {
	query, ok := strictPageQueryWithDirectionParam(c, "sortDirection", map[string]struct{}{
		"orderNo":                 {},
		"supplierName":            {},
		"orderDate":               {},
		"materialCode":            {},
		"pendingInvoiceWeightTon": {},
		"pendingInvoiceAmount":    {},
	})
	if !ok {
		return
	}
	result, err := h.resolvePendingInvoiceReceiptReportProvider().Page(c.Request.Context(), query, platform.PendingInvoiceReceiptReportFilter{
		Keyword:      c.Query("keyword"),
		SupplierName: c.Query("supplierName"),
		StartDate:    c.Query("startDate"),
		EndDate:      c.Query("endDate"),
	})
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func writeFileDownload(c *gin.Context, file platform.FileDownloadResponse) {
	c.Header("Content-Disposition", mime.FormatMediaType("attachment", map[string]string{
		"filename": file.Filename,
	}))
	c.Header("Content-Length", strconv.Itoa(len(file.Content)))
	c.Data(http.StatusOK, file.ContentType, file.Content)
}

func (h apiHandler) generalSettingPage(c *gin.Context) {
	result, err := h.resolveGeneralSettingProvider().Page(c.Request.Context(), pageQuery(c), c.Query("keyword"), c.Query("status"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) generalSettingDisplaySwitches(c *gin.Context) {
	result, err := h.resolveGeneralSettingProvider().PublicDisplaySwitches(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) generalSettingClientSettings(c *gin.Context) {
	result, err := h.resolveGeneralSettingProvider().PublicClientSettings(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) generalSettingStatementGeneratorRules(c *gin.Context) {
	result, err := h.resolveGeneralSettingProvider().StatementGeneratorRules(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) generalSettingDetail(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolveGeneralSettingProvider().Detail(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) generalSettingNextNumber(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	moduleKey := strings.TrimSpace(c.Query("moduleKey"))
	if moduleKey == "" {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "moduleKey不能为空")
		return
	}
	resource := moduleKey
	if resolved, ok := platform.ResourceForMenuCode(moduleKey); ok {
		resource = resolved
	}
	if err := h.resolvePermissionChecker().Require(c.Request.Context(), principal.UserID, resource, "create"); err != nil {
		writeAuthError(c, err)
		return
	}
	result, err := h.resolveGeneralSettingProvider().NextNumber(c.Request.Context(), moduleKey)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) generalSettingCreate(c *gin.Context) {
	var req platform.NoRuleRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveGeneralSettingProvider().Create(c.Request.Context(), req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "创建成功", result)
}

func (h apiHandler) generalSettingUpdate(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	var req platform.NoRuleRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveGeneralSettingProvider().Update(c.Request.Context(), id, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "更新成功", result)
}

func (h apiHandler) generalSettingDelete(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	if err := h.resolveGeneralSettingProvider().Delete(c.Request.Context(), id); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "删除成功", nil)
}

func (h apiHandler) uploadRuleDetail(c *gin.Context) {
	result, err := h.resolveUploadRuleProvider().Detail(c.Request.Context(), c.Query("moduleKey"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) uploadRuleUpdate(c *gin.Context) {
	var req platform.UploadRuleRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveUploadRuleProvider().Update(c.Request.Context(), c.Query("moduleKey"), req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "更新成功", result)
}

func (h apiHandler) materialCategoryPage(c *gin.Context) {
	query, ok := strictPageQuery(c, nil)
	if !ok {
		return
	}
	result, err := h.resolveMaterialCategoryProvider().Page(c.Request.Context(), query, c.Query("keyword"), c.Query("status"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) materialCategoryDetail(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolveMaterialCategoryProvider().Detail(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) materialCategoryCreate(c *gin.Context) {
	var req platform.MaterialCategoryRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveMaterialCategoryProvider().Create(c.Request.Context(), req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "创建成功", result)
}

func (h apiHandler) materialCategoryUpdate(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	var req platform.MaterialCategoryRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveMaterialCategoryProvider().Update(c.Request.Context(), id, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "更新成功", result)
}

func (h apiHandler) materialCategoryDelete(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	if err := h.resolveMaterialCategoryProvider().Delete(c.Request.Context(), id); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "删除成功", nil)
}

func (h apiHandler) materialCategoryOptions(c *gin.Context) {
	result, err := h.resolveMaterialCategoryProvider().Options(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) supplierOptions(c *gin.Context) {
	result, err := h.resolveSupplierProvider().Options(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) supplierPage(c *gin.Context) {
	query, ok := strictPageQuery(c, map[string]struct{}{
		"id":           {},
		"supplierCode": {},
		"supplierName": {},
		"contactName":  {},
		"contactPhone": {},
		"city":         {},
		"status":       {},
	})
	if !ok {
		return
	}
	result, err := h.resolveSupplierProvider().Page(c.Request.Context(), query, c.Query("keyword"), c.Query("status"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) supplierDetail(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolveSupplierProvider().Detail(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) supplierCreate(c *gin.Context) {
	var req platform.SupplierRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveSupplierProvider().Create(c.Request.Context(), req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "创建成功", result)
}

func (h apiHandler) supplierUpdate(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	var req platform.SupplierRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveSupplierProvider().Update(c.Request.Context(), id, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "更新成功", result)
}

func (h apiHandler) supplierDelete(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	if err := h.resolveSupplierProvider().Delete(c.Request.Context(), id); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "删除成功", nil)
}

func (h apiHandler) customerOptions(c *gin.Context) {
	result, err := h.resolveCustomerProvider().Options(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) customerPage(c *gin.Context) {
	query, ok := strictPageQuery(c, map[string]struct{}{
		"id":             {},
		"customerCode":   {},
		"customerName":   {},
		"contactName":    {},
		"contactPhone":   {},
		"city":           {},
		"settlementMode": {},
		"projectName":    {},
		"status":         {},
	})
	if !ok {
		return
	}
	result, err := h.resolveCustomerProvider().Page(c.Request.Context(), query, c.Query("keyword"), c.Query("status"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) customerDetail(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolveCustomerProvider().Detail(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) customerCreate(c *gin.Context) {
	var req platform.CustomerRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveCustomerProvider().Create(c.Request.Context(), req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "创建成功", result)
}

func (h apiHandler) customerUpdate(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	var req platform.CustomerRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveCustomerProvider().Update(c.Request.Context(), id, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "更新成功", result)
}

func (h apiHandler) customerDelete(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	if err := h.resolveCustomerProvider().Delete(c.Request.Context(), id); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "删除成功", nil)
}

func (h apiHandler) projectPage(c *gin.Context) {
	query, ok := strictPageQuery(c, map[string]struct{}{
		"id":              {},
		"projectCode":     {},
		"projectName":     {},
		"projectNameAbbr": {},
		"customerCode":    {},
		"projectManager":  {},
		"status":          {},
	})
	if !ok {
		return
	}
	result, err := h.resolveProjectProvider().Page(c.Request.Context(), query, c.Query("keyword"), c.Query("status"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) projectDetail(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolveProjectProvider().Detail(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) projectCreate(c *gin.Context) {
	var req platform.ProjectRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveProjectProvider().Create(c.Request.Context(), req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "创建成功", result)
}

func (h apiHandler) projectUpdate(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	var req platform.ProjectRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveProjectProvider().Update(c.Request.Context(), id, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "更新成功", result)
}

func (h apiHandler) projectDelete(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	if err := h.resolveProjectProvider().Delete(c.Request.Context(), id); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "删除成功", nil)
}

func (h apiHandler) carrierOptions(c *gin.Context) {
	result, err := h.resolveCarrierProvider().Options(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) carrierPage(c *gin.Context) {
	query, ok := strictPageQuery(c, map[string]struct{}{
		"id":           {},
		"carrierCode":  {},
		"carrierName":  {},
		"contactName":  {},
		"contactPhone": {},
		"vehicleType":  {},
		"priceMode":    {},
		"status":       {},
	})
	if !ok {
		return
	}
	result, err := h.resolveCarrierProvider().Page(c.Request.Context(), query, c.Query("keyword"), c.Query("status"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) carrierDetail(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolveCarrierProvider().Detail(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) carrierCreate(c *gin.Context) {
	var req platform.CarrierRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveCarrierProvider().Create(c.Request.Context(), req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "创建成功", result)
}

func (h apiHandler) carrierUpdate(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	var req platform.CarrierRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveCarrierProvider().Update(c.Request.Context(), id, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "更新成功", result)
}

func (h apiHandler) carrierDelete(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	if err := h.resolveCarrierProvider().Delete(c.Request.Context(), id); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "删除成功", nil)
}

func (h apiHandler) warehouseOptions(c *gin.Context) {
	result, err := h.resolveWarehouseProvider().Options(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) warehousePage(c *gin.Context) {
	query, ok := strictPageQuery(c, map[string]struct{}{
		"id":            {},
		"warehouseCode": {},
		"warehouseName": {},
		"warehouseType": {},
		"contactName":   {},
		"contactPhone":  {},
		"address":       {},
		"status":        {},
	})
	if !ok {
		return
	}
	result, err := h.resolveWarehouseProvider().Page(
		c.Request.Context(),
		query,
		c.Query("keyword"),
		c.Query("warehouseType"),
		c.Query("status"),
	)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) warehouseDetail(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolveWarehouseProvider().Detail(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) warehouseCreate(c *gin.Context) {
	var req platform.WarehouseRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveWarehouseProvider().Create(c.Request.Context(), req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "创建成功", result)
}

func (h apiHandler) warehouseUpdate(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	var req platform.WarehouseRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveWarehouseProvider().Update(c.Request.Context(), id, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "更新成功", result)
}

func (h apiHandler) warehouseDelete(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	if err := h.resolveWarehouseProvider().Delete(c.Request.Context(), id); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "删除成功", nil)
}

func (h apiHandler) rateLimitRuleList(c *gin.Context) {
	result, err := h.resolveRateLimitAdminProvider().ListRules(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) rateLimitRuleUpdate(c *gin.Context) {
	id, err := strconv.ParseInt(strings.TrimSpace(c.Param("id")), 10, 64)
	if err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "id: 参数格式错误")
		return
	}
	var req map[string]any
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	if req == nil {
		writeFailure(c, http.StatusInternalServerError, codeInternalError, "系统异常")
		return
	}
	if err := h.resolveRateLimitAdminProvider().UpdateRule(c.Request.Context(), id, req); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", nil)
}

func (h apiHandler) verifySensitiveOperationTOTP(c *gin.Context) bool {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return false
	}
	if err := h.resolveAuthProvider().VerifyCurrentUserTOTP(c.Request.Context(), principal.UserID, c.GetHeader("X-TOTP-Code")); err != nil {
		writeAuthError(c, err)
		return false
	}
	return true
}

func (h apiHandler) requireModulePermissionAny(c *gin.Context, userID int64, moduleKey string, actions ...string) error {
	moduleKey = strings.TrimSpace(moduleKey)
	if moduleKey == "" {
		return platform.NewAuthError(platform.AuthErrorValidation, "缺少模块标识")
	}
	resource := moduleKey
	if resolved, ok := platform.ResourceForMenuCode(moduleKey); ok {
		resource = resolved
	}
	if len(actions) == 0 {
		return platform.NewAuthError(platform.AuthErrorInternal, "权限动作配置错误")
	}
	var lastErr error
	for _, action := range actions {
		action = strings.TrimSpace(action)
		if action == "" {
			continue
		}
		if err := h.resolvePermissionChecker().Require(c.Request.Context(), userID, resource, action); err == nil {
			return nil
		} else {
			lastErr = err
		}
	}
	if lastErr != nil {
		var authErr platform.AuthError
		if errors.As(lastErr, &authErr) && authErr.Kind == platform.AuthErrorForbidden {
			return platform.NewAuthError(platform.AuthErrorForbidden, "无操作权限")
		}
		return lastErr
	}
	return platform.NewAuthError(platform.AuthErrorInternal, "权限动作配置错误")
}

func (h apiHandler) securityKeyOverview(c *gin.Context) {
	result, err := h.resolveSecurityKeyProvider().Overview(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) securityKeyRotateJWT(c *gin.Context) {
	if !h.verifySensitiveOperationTOTP(c) {
		return
	}
	result, err := h.resolveSecurityKeyProvider().RotateJWT(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "JWT 主密钥轮转成功", result)
}

func (h apiHandler) securityKeyRotateTOTP(c *gin.Context) {
	if !h.verifySensitiveOperationTOTP(c) {
		return
	}
	result, err := h.resolveSecurityKeyProvider().RotateTOTP(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "2FA 主密钥轮转成功", result)
}

func (h apiHandler) printTemplateList(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	billType := strings.TrimSpace(c.Query("billType"))
	if err := h.requireModulePermissionAny(c, principal.UserID, billType, "print", "read"); err != nil {
		writeAuthError(c, err)
		return
	}
	result, err := h.resolvePrintTemplateProvider().ListByBillType(c.Request.Context(), billType)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) printTemplateCreate(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	var req platform.PrintTemplateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	if err := h.requireModulePermissionAny(c, principal.UserID, req.BillType, "update"); err != nil {
		writeAuthError(c, err)
		return
	}
	result, err := h.resolvePrintTemplateProvider().Create(c.Request.Context(), req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "创建成功", result)
}

func (h apiHandler) printTemplateUpdate(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	id, ok := pathID(c)
	if !ok {
		return
	}
	var req platform.PrintTemplateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	currentBillType, err := h.resolvePrintTemplateProvider().GetBillType(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	if err := h.requireModulePermissionAny(c, principal.UserID, currentBillType, "update"); err != nil {
		writeAuthError(c, err)
		return
	}
	if strings.TrimSpace(req.BillType) != "" && strings.TrimSpace(req.BillType) != strings.TrimSpace(currentBillType) {
		if err := h.requireModulePermissionAny(c, principal.UserID, req.BillType, "update"); err != nil {
			writeAuthError(c, err)
			return
		}
	}
	result, err := h.resolvePrintTemplateProvider().Update(c.Request.Context(), id, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "更新成功", result)
}

func (h apiHandler) printTemplateUploadJSON(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	id, ok := pathID(c)
	if !ok {
		return
	}
	currentBillType, err := h.resolvePrintTemplateProvider().GetBillType(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	if err := h.requireModulePermissionAny(c, principal.UserID, currentBillType, "update"); err != nil {
		writeAuthError(c, err)
		return
	}
	file, err := c.FormFile("file")
	if err != nil {
		writeAuthError(c, platform.NewAuthError(platform.AuthErrorValidation, "上传 JSON 文件不能为空"))
		return
	}
	opened, err := file.Open()
	if err != nil {
		writeAuthError(c, platform.NewAuthError(platform.AuthErrorInternal, "读取上传 JSON 文件失败"))
		return
	}
	defer opened.Close()
	content, err := io.ReadAll(io.LimitReader(opened, platform.MaxPrintTemplateUploadJSONBytes+1))
	if err != nil {
		writeAuthError(c, platform.NewAuthError(platform.AuthErrorInternal, "读取上传 JSON 文件失败"))
		return
	}
	result, err := h.resolvePrintTemplateProvider().UploadJSON(c.Request.Context(), id, filepath.Base(file.Filename), content)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "上传成功", result)
}

func (h apiHandler) printTemplateDelete(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	id, ok := pathID(c)
	if !ok {
		return
	}
	currentBillType, err := h.resolvePrintTemplateProvider().GetBillType(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	if err := h.requireModulePermissionAny(c, principal.UserID, currentBillType, "update"); err != nil {
		writeAuthError(c, err)
		return
	}
	if err := h.resolvePrintTemplateProvider().Delete(c.Request.Context(), id); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "删除成功", nil)
}

func (h apiHandler) companyPage(c *gin.Context) {
	result, err := h.resolveCompanyProvider().Page(c.Request.Context(), pageQuery(c), c.Query("keyword"), c.Query("status"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) companyDetail(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolveCompanyProvider().Detail(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) companyName(c *gin.Context) {
	current, err := h.resolveCompanyProvider().Current(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	if current == nil {
		writeSuccess(c, http.StatusOK, codeSuccess.Message, "")
		return
	}
	writeSuccess(c, http.StatusOK, codeSuccess.Message, current.CompanyName)
}

func (h apiHandler) companyCurrent(c *gin.Context) {
	result, err := h.resolveCompanyProvider().Current(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) companySaveCurrent(c *gin.Context) {
	var req platform.CompanySetting
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveCompanyProvider().SaveCurrent(c.Request.Context(), req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "保存成功", result)
}

func (h apiHandler) companyCreate(c *gin.Context) {
	var req platform.CompanySetting
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveCompanyProvider().Create(c.Request.Context(), req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "创建成功", result)
}

func (h apiHandler) companyUpdate(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	var req platform.CompanySetting
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveCompanyProvider().Update(c.Request.Context(), id, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "更新成功", result)
}

func (h apiHandler) companyDelete(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	if err := h.resolveCompanyProvider().Delete(c.Request.Context(), id); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "删除成功", nil)
}

func (h apiHandler) roleSettingPage(c *gin.Context) {
	result, err := h.resolveRoleSettingProvider().Page(c.Request.Context(), pageQuery(c), c.Query("keyword"), c.Query("status"))
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) roleSettingDetail(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolveRoleSettingProvider().Detail(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) roleSettingCreate(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	var req platform.RoleSettingRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveRoleSettingProvider().Create(c.Request.Context(), principal.UserID, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "创建成功", result)
}

func (h apiHandler) roleSettingUpdate(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	id, ok := pathID(c)
	if !ok {
		return
	}
	var req platform.RoleSettingRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	result, err := h.resolveRoleSettingProvider().Update(c.Request.Context(), principal.UserID, id, req)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "更新成功", result)
}

func (h apiHandler) roleSettingDelete(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	if err := h.resolveRoleSettingProvider().Delete(c.Request.Context(), id); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "删除成功", nil)
}

func (h apiHandler) roleSettingPermissions(c *gin.Context) {
	id, ok := pathID(c)
	if !ok {
		return
	}
	result, err := h.resolveRoleSettingProvider().Permissions(c.Request.Context(), id)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) roleSettingSavePermissions(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	id, ok := pathID(c)
	if !ok {
		return
	}
	var req []platform.RolePermissionItem
	if err := c.ShouldBindJSON(&req); err != nil {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "请求体格式错误，请检查JSON格式")
		return
	}
	if err := h.resolveRoleSettingProvider().SavePermissions(c.Request.Context(), principal.UserID, id, req); err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "权限保存成功", nil)
}

func (h apiHandler) roleSettingPermissionOptions(c *gin.Context) {
	result, err := h.resolveRoleSettingProvider().PermissionOptions(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) roleSettingTemplates(c *gin.Context) {
	result, err := h.resolveRoleSettingProvider().Templates(c.Request.Context())
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) databaseStatus(c *gin.Context) {
	writeSuccess(c, http.StatusOK, "", h.resolveDatabaseProvider().Status(c.Request.Context()))
}

func (h apiHandler) databaseMonitoring(c *gin.Context) {
	writeSuccess(c, http.StatusOK, "", h.resolveDatabaseProvider().Monitoring(c.Request.Context()))
}

func (h apiHandler) globalSearch(c *gin.Context) {
	principal, ok := currentPrincipal(c)
	if !ok {
		writeFailure(c, http.StatusUnauthorized, codeUnauthorized, "未登录")
		return
	}
	limit, err := strconv.Atoi(c.DefaultQuery("limit", "20"))
	if err != nil {
		limit = 20
	}
	result, err := h.resolveGlobalSearcher().Search(
		c.Request.Context(),
		principal.UserID,
		c.Query("keyword"),
		limit,
		c.QueryArray("moduleKeys"),
	)
	if err != nil {
		writeAuthError(c, err)
		return
	}
	writeSuccess(c, http.StatusOK, "", result)
}

func (h apiHandler) metaCode(c *gin.Context) {
	writeSuccess(c, http.StatusOK, "", metaCodeResponse{
		ErrorCodes:     publicErrorCodes(),
		ResourceLabels: resourceLabels(),
		ActionLabels:   actionLabels(),
	})
}

func (h apiHandler) resolveHealthChecker() HealthChecker {
	if h.healthChecker != nil {
		return h.healthChecker
	}
	return staticHealthChecker{health: platform.Health{
		DB:    platform.HealthCheck{Status: "DOWN"},
		Redis: platform.HealthCheck{Status: "DOWN"},
		Disk:  platform.HealthCheck{Status: "DOWN"},
	}}
}

func (h apiHandler) resolveSetupStatusProvider() SetupStatusProvider {
	if h.setupStatusProvider != nil {
		return h.setupStatusProvider
	}
	return staticSetupStatusProvider{status: platform.SetupStatus{
		SetupRequired:     h.cfg.SetupRequired,
		AdminConfigured:   h.cfg.AdminConfigured,
		CompanyConfigured: h.cfg.CompanyConfigured,
	}}
}

func (h apiHandler) resolveCaptchaGenerator() CaptchaGenerator {
	if h.captchaGenerator != nil {
		return h.captchaGenerator
	}
	return staticCaptchaGenerator{}
}

func (h apiHandler) resolveCaptchaVerifier() platform.CaptchaVerifier {
	if verifier, ok := h.resolveCaptchaGenerator().(platform.CaptchaVerifier); ok {
		return verifier
	}
	return nil
}

func (h apiHandler) resolveAuthProvider() AuthProvider {
	if h.authProvider != nil {
		return h.authProvider
	}
	return staticAuthProvider{}
}

func (h apiHandler) resolveAccountSecurityWriter() AccountSecurityWriter {
	if writer, ok := h.resolveAuthProvider().(AccountSecurityWriter); ok {
		return writer
	}
	return staticAccountSecurityWriter{}
}

func (h apiHandler) resolveSetupInitializer() SetupInitializer {
	if h.setupInitializer != nil {
		return h.setupInitializer
	}
	return staticSetupInitializer{}
}

func (h apiHandler) resolvePermissionChecker() PermissionChecker {
	if h.permissionChecker != nil {
		return h.permissionChecker
	}
	return staticPermissionChecker{}
}

func (h apiHandler) resolveDashboardProvider() DashboardSummaryProvider {
	if h.dashboardProvider != nil {
		return h.dashboardProvider
	}
	return staticDashboardProvider{}
}

func (h apiHandler) resolveDepartmentProvider() DepartmentProvider {
	if h.departmentProvider != nil {
		return h.departmentProvider
	}
	return staticDepartmentProvider{}
}

func (h apiHandler) resolveOperationLogProvider() OperationLogProvider {
	if h.operationLog != nil {
		return h.operationLog
	}
	return staticOperationLogProvider{}
}

func (h apiHandler) resolveGeneralSettingProvider() GeneralSettingProvider {
	if h.generalSetting != nil {
		return h.generalSetting
	}
	return staticGeneralSettingProvider{}
}

func (h apiHandler) resolveUploadRuleProvider() UploadRuleProvider {
	if h.uploadRule != nil {
		return h.uploadRule
	}
	return staticUploadRuleProvider{}
}

func (h apiHandler) resolveSecurityKeyProvider() SecurityKeyProvider {
	if h.securityKey != nil {
		return h.securityKey
	}
	return staticSecurityKeyProvider{}
}

func (h apiHandler) resolveMenuProvider() MenuTreeProvider {
	if h.menuProvider != nil {
		return h.menuProvider
	}
	return staticMenuProvider{}
}

func (h apiHandler) resolveCompanyProvider() CompanySettingsProvider {
	if h.companyProvider != nil {
		return h.companyProvider
	}
	return staticCompanyProvider{}
}

func (h apiHandler) resolveDatabaseProvider() DatabaseStatusProvider {
	if h.databaseProvider != nil {
		return h.databaseProvider
	}
	return staticDatabaseProvider{}
}

func (h apiHandler) resolveIoReportProvider() IoReportProvider {
	if h.ioReport != nil {
		return h.ioReport
	}
	return staticIoReportProvider{}
}

func (h apiHandler) resolveInventoryReportProvider() InventoryReportProvider {
	if h.inventoryReport != nil {
		return h.inventoryReport
	}
	return staticInventoryReportProvider{}
}

func (h apiHandler) resolvePendingInvoiceReceiptReportProvider() PendingInvoiceReceiptReportProvider {
	if h.pendingInvoiceReceiptReport != nil {
		return h.pendingInvoiceReceiptReport
	}
	return staticPendingInvoiceReceiptReportProvider{}
}

func (h apiHandler) resolveGlobalSearcher() GlobalSearcher {
	if h.globalSearcher != nil {
		return h.globalSearcher
	}
	return staticGlobalSearcher{}
}

func (h apiHandler) resolvePermissionEntryProvider() PermissionEntryProvider {
	if h.permissionEntry != nil {
		return h.permissionEntry
	}
	return staticPermissionEntryProvider{}
}

func (h apiHandler) resolveRoleSettingProvider() RoleSettingProvider {
	if h.roleSetting != nil {
		return h.roleSetting
	}
	return staticRoleSettingProvider{}
}

func (h apiHandler) resolveUserAccountProvider() UserAccountAdminProvider {
	if h.userAccount != nil {
		return h.userAccount
	}
	return staticUserAccountProvider{}
}

func (h apiHandler) resolveRefreshTokenProvider() RefreshTokenAdminProvider {
	if h.refreshToken != nil {
		return h.refreshToken
	}
	return staticRefreshTokenProvider{}
}

func (h apiHandler) resolveApiKeyProvider() ApiKeyAdminProvider {
	if h.apiKey != nil {
		return h.apiKey
	}
	return staticApiKeyProvider{}
}

func (h apiHandler) resolvePrintTemplateProvider() PrintTemplateProvider {
	if h.printTemplate != nil {
		return h.printTemplate
	}
	return staticPrintTemplateProvider{}
}

func (h apiHandler) resolveMaterialCategoryProvider() MaterialCategoryProvider {
	if h.materialCategory != nil {
		return h.materialCategory
	}
	return staticMaterialCategoryProvider{}
}

func (h apiHandler) resolveSupplierProvider() SupplierProvider {
	if h.supplier != nil {
		return h.supplier
	}
	return staticSupplierProvider{}
}

func (h apiHandler) resolveCustomerProvider() CustomerProvider {
	if h.customer != nil {
		return h.customer
	}
	return staticCustomerProvider{}
}

func (h apiHandler) resolveProjectProvider() ProjectProvider {
	if h.project != nil {
		return h.project
	}
	return staticProjectProvider{}
}

func (h apiHandler) resolveCarrierProvider() CarrierProvider {
	if h.carrier != nil {
		return h.carrier
	}
	return staticCarrierProvider{}
}

func (h apiHandler) resolveWarehouseProvider() WarehouseProvider {
	if h.warehouse != nil {
		return h.warehouse
	}
	return staticWarehouseProvider{}
}

func (h apiHandler) resolveRateLimitAdminProvider() RateLimitAdminProvider {
	if h.rateLimit != nil {
		return h.rateLimit
	}
	return staticRateLimitAdminProvider{}
}

type staticHealthChecker struct {
	health platform.Health
}

func (s staticHealthChecker) Check(context.Context) platform.Health {
	return s.health
}

type staticSetupStatusProvider struct {
	status platform.SetupStatus
	err    error
}

func (s staticSetupStatusProvider) Status(context.Context) (platform.SetupStatus, error) {
	return s.status, s.err
}

type staticCaptchaGenerator struct {
	captcha platform.Captcha
	err     error
}

func (s staticCaptchaGenerator) Generate(context.Context) (platform.Captcha, error) {
	return s.captcha, s.err
}

type staticAuthProvider struct{}

func (staticAuthProvider) Login(context.Context, platform.LoginRequest, string, string, platform.CaptchaVerifier) (platform.LoginResponseBody, error) {
	return platform.LoginResponseBody{}, platform.NewAuthError(platform.AuthErrorInternal, "认证服务未配置")
}

func (staticAuthProvider) Login2FA(context.Context, platform.Login2FARequest, string, string) (platform.TokenResponse, error) {
	return platform.TokenResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "认证服务未配置")
}

func (staticAuthProvider) Refresh(context.Context, string, string, string) (platform.TokenResponse, error) {
	return platform.TokenResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "认证服务未配置")
}

func (staticAuthProvider) Logout(context.Context, string) error {
	return nil
}

func (staticAuthProvider) AuthenticateAccessToken(context.Context, string) (platform.AuthenticatedPrincipal, error) {
	return platform.AuthenticatedPrincipal{}, platform.NewAuthError(platform.AuthErrorUnauthorized, "未登录")
}

func (staticAuthProvider) CurrentUserSecurityStatus(context.Context, int64) (platform.CurrentUserSecurityResponse, error) {
	return platform.CurrentUserSecurityResponse{}, platform.NewAuthError(platform.AuthErrorUnauthorized, "未登录")
}

func (staticAuthProvider) VerifyCurrentUserTOTP(context.Context, int64, string) error {
	return platform.NewAuthError(platform.AuthErrorUnauthorized, "未登录")
}

type staticAccountSecurityWriter struct{}

func (staticAccountSecurityWriter) ChangeCurrentUserPassword(context.Context, int64, string, string) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "认证服务未配置")
}

func (staticAccountSecurityWriter) SetupCurrentUser2FA(context.Context, int64) (string, string, error) {
	return "", "", platform.NewAuthError(platform.AuthErrorInternal, "认证服务未配置")
}

func (staticAccountSecurityWriter) EnableCurrentUser2FA(context.Context, int64, string) (platform.CurrentUserSecurityResponse, error) {
	return platform.CurrentUserSecurityResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "认证服务未配置")
}

func (staticAccountSecurityWriter) DisableCurrentUser2FA(context.Context, int64, string) (platform.CurrentUserSecurityResponse, error) {
	return platform.CurrentUserSecurityResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "认证服务未配置")
}

type staticSetupInitializer struct{}

func (staticSetupInitializer) Initialize(context.Context, platform.InitialSetupSubmitRequest) (platform.InitialSetupSubmitResponse, error) {
	return platform.InitialSetupSubmitResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "初始化服务未配置")
}

func (staticSetupInitializer) SetupAdminTotp(context.Context, platform.InitialSetupTotpSetupRequest) (platform.TotpSetupResponse, error) {
	return platform.TotpSetupResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "初始化服务未配置")
}

func (staticSetupInitializer) ConfigureAdmin(context.Context, platform.InitialSetupAdminSubmitRequest) (platform.InitialSetupSubmitResponse, error) {
	return platform.InitialSetupSubmitResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "初始化服务未配置")
}

func (staticSetupInitializer) ConfigureCompany(context.Context, platform.InitialSetupCompanyRequest) (platform.InitialSetupSubmitResponse, error) {
	return platform.InitialSetupSubmitResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "初始化服务未配置")
}

type staticPermissionChecker struct{}

func (staticPermissionChecker) Require(context.Context, int64, string, string) error {
	return platform.NewAuthError(platform.AuthErrorForbidden, "无权访问")
}

type staticDashboardProvider struct{}

func (staticDashboardProvider) Summary(context.Context, int64) (platform.DashboardSummary, error) {
	return platform.DashboardSummary{}, platform.NewAuthError(platform.AuthErrorInternal, "工作台服务未配置")
}

type staticDepartmentProvider struct{}

func (staticDepartmentProvider) Page(context.Context, platform.PageQuery, string, string) (platform.PageResponse[platform.DepartmentResponse], error) {
	return platform.PageResponse[platform.DepartmentResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "部门服务未配置")
}

func (staticDepartmentProvider) Options(context.Context) ([]platform.DepartmentOptionResponse, error) {
	return []platform.DepartmentOptionResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "部门服务未配置")
}

func (staticDepartmentProvider) Detail(context.Context, int64) (platform.DepartmentResponse, error) {
	return platform.DepartmentResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "部门服务未配置")
}

func (staticDepartmentProvider) Create(context.Context, platform.DepartmentRequest) (platform.DepartmentResponse, error) {
	return platform.DepartmentResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "部门服务未配置")
}

func (staticDepartmentProvider) Update(context.Context, int64, platform.DepartmentRequest) (platform.DepartmentResponse, error) {
	return platform.DepartmentResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "部门服务未配置")
}

func (staticDepartmentProvider) Delete(context.Context, int64) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "部门服务未配置")
}

type staticOperationLogProvider struct{}

func (staticOperationLogProvider) Page(context.Context, platform.PageQuery, platform.OperationLogFilter) (platform.PageResponse[platform.OperationLogResponse], error) {
	return platform.PageResponse[platform.OperationLogResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "操作日志服务未配置")
}

type staticGeneralSettingProvider struct{}

func (staticGeneralSettingProvider) Page(context.Context, platform.PageQuery, string, string) (platform.PageResponse[platform.GeneralSettingResponse], error) {
	return platform.PageResponse[platform.GeneralSettingResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "通用设置服务未配置")
}

func (staticGeneralSettingProvider) PublicDisplaySwitches(context.Context) ([]platform.GeneralSettingResponse, error) {
	return []platform.GeneralSettingResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "通用设置服务未配置")
}

func (staticGeneralSettingProvider) PublicClientSettings(context.Context) ([]platform.GeneralSettingResponse, error) {
	return []platform.GeneralSettingResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "通用设置服务未配置")
}

func (staticGeneralSettingProvider) StatementGeneratorRules(context.Context) (platform.StatementGeneratorRulesResponse, error) {
	return platform.StatementGeneratorRulesResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "通用设置服务未配置")
}

func (staticGeneralSettingProvider) Detail(context.Context, int64) (platform.NoRuleResponse, error) {
	return platform.NoRuleResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "通用设置服务未配置")
}

func (staticGeneralSettingProvider) NextNumber(context.Context, string) (platform.NoRuleGenerateResponse, error) {
	return platform.NoRuleGenerateResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "通用设置服务未配置")
}

func (staticGeneralSettingProvider) Create(context.Context, platform.NoRuleRequest) (platform.NoRuleResponse, error) {
	return platform.NoRuleResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "通用设置服务未配置")
}

func (staticGeneralSettingProvider) Update(context.Context, int64, platform.NoRuleRequest) (platform.NoRuleResponse, error) {
	return platform.NoRuleResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "通用设置服务未配置")
}

func (staticGeneralSettingProvider) Delete(context.Context, int64) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "通用设置服务未配置")
}

type staticUploadRuleProvider struct{}

func (staticUploadRuleProvider) Detail(context.Context, string) (platform.UploadRuleResponse, error) {
	return platform.UploadRuleResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "上传规则服务未配置")
}

func (staticUploadRuleProvider) Update(context.Context, string, platform.UploadRuleRequest) (platform.UploadRuleResponse, error) {
	return platform.UploadRuleResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "上传规则服务未配置")
}

type staticSecurityKeyProvider struct{}

func (staticSecurityKeyProvider) Overview(context.Context) (platform.SecurityKeyOverviewResponse, error) {
	return platform.SecurityKeyOverviewResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "安全密钥服务未配置")
}

func (staticSecurityKeyProvider) RotateJWT(context.Context) (platform.SecurityKeyRotateResponse, error) {
	return platform.SecurityKeyRotateResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "安全密钥服务未配置")
}

func (staticSecurityKeyProvider) RotateTOTP(context.Context) (platform.SecurityKeyRotateResponse, error) {
	return platform.SecurityKeyRotateResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "安全密钥服务未配置")
}

type staticMenuProvider struct{}

func (staticMenuProvider) Tree(context.Context, int64) ([]platform.MenuNode, error) {
	return []platform.MenuNode{}, platform.NewAuthError(platform.AuthErrorInternal, "菜单服务未配置")
}

type staticCompanyProvider struct{}

func (staticCompanyProvider) Page(context.Context, platform.PageQuery, string, string) (platform.PageResponse[platform.CompanySetting], error) {
	return platform.PageResponse[platform.CompanySetting]{}, platform.NewAuthError(platform.AuthErrorInternal, "公司信息服务未配置")
}

func (staticCompanyProvider) Detail(context.Context, int64) (platform.CompanySetting, error) {
	return platform.CompanySetting{}, platform.NewAuthError(platform.AuthErrorInternal, "公司信息服务未配置")
}

func (staticCompanyProvider) Current(context.Context) (*platform.CompanySetting, error) {
	return nil, platform.NewAuthError(platform.AuthErrorInternal, "公司信息服务未配置")
}

func (staticCompanyProvider) SaveCurrent(context.Context, platform.CompanySetting) (platform.CompanySetting, error) {
	return platform.CompanySetting{}, platform.NewAuthError(platform.AuthErrorInternal, "公司信息服务未配置")
}

func (staticCompanyProvider) Create(context.Context, platform.CompanySetting) (platform.CompanySetting, error) {
	return platform.CompanySetting{}, platform.NewAuthError(platform.AuthErrorInternal, "公司信息服务未配置")
}

func (staticCompanyProvider) Update(context.Context, int64, platform.CompanySetting) (platform.CompanySetting, error) {
	return platform.CompanySetting{}, platform.NewAuthError(platform.AuthErrorInternal, "公司信息服务未配置")
}

func (staticCompanyProvider) Delete(context.Context, int64) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "公司信息服务未配置")
}

type staticDatabaseProvider struct{}

func (staticDatabaseProvider) Status(context.Context) platform.DatabaseStatus {
	return platform.DatabaseStatus{}
}

func (staticDatabaseProvider) Monitoring(context.Context) platform.DatabaseMonitoring {
	return platform.DatabaseMonitoring{}
}

type staticIoReportProvider struct{}

func (staticIoReportProvider) Page(context.Context, platform.PageQuery, platform.IoReportFilter) (platform.PageResponse[platform.IoReportResponse], error) {
	return platform.PageResponse[platform.IoReportResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "出入库报表服务未配置")
}

type staticInventoryReportProvider struct{}

func (staticInventoryReportProvider) Page(context.Context, platform.PageQuery, platform.InventoryReportFilter) (platform.PageResponse[platform.InventoryReportResponse], error) {
	return platform.PageResponse[platform.InventoryReportResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "库存报表服务未配置")
}

func (staticInventoryReportProvider) ExportExcel(context.Context, string, string, string) (platform.FileDownloadResponse, error) {
	return platform.FileDownloadResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "库存报表服务未配置")
}

type staticPendingInvoiceReceiptReportProvider struct{}

func (staticPendingInvoiceReceiptReportProvider) Page(context.Context, platform.PageQuery, platform.PendingInvoiceReceiptReportFilter) (platform.PageResponse[platform.PendingInvoiceReceiptReportResponse], error) {
	return platform.PageResponse[platform.PendingInvoiceReceiptReportResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "未收票报表服务未配置")
}

type staticGlobalSearcher struct{}

func (staticGlobalSearcher) Search(context.Context, int64, string, int, []string) ([]platform.GlobalSearchResult, error) {
	return []platform.GlobalSearchResult{}, platform.NewAuthError(platform.AuthErrorInternal, "全局搜索服务未配置")
}

type staticPermissionEntryProvider struct{}

func (staticPermissionEntryProvider) Page(context.Context, platform.PageQuery, string) (platform.PageResponse[platform.PermissionEntryResponse], error) {
	return platform.PageResponse[platform.PermissionEntryResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "权限服务未配置")
}

func (staticPermissionEntryProvider) Detail(context.Context, int64) (platform.PermissionEntryResponse, error) {
	return platform.PermissionEntryResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "权限服务未配置")
}

type staticRoleSettingProvider struct{}

func (staticRoleSettingProvider) Page(context.Context, platform.PageQuery, string, string) (platform.PageResponse[platform.RoleSettingResponse], error) {
	return platform.PageResponse[platform.RoleSettingResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "角色服务未配置")
}

func (staticRoleSettingProvider) Detail(context.Context, int64) (platform.RoleSettingResponse, error) {
	return platform.RoleSettingResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "角色服务未配置")
}

func (staticRoleSettingProvider) Create(context.Context, int64, platform.RoleSettingRequest) (platform.RoleSettingResponse, error) {
	return platform.RoleSettingResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "角色服务未配置")
}

func (staticRoleSettingProvider) Update(context.Context, int64, int64, platform.RoleSettingRequest) (platform.RoleSettingResponse, error) {
	return platform.RoleSettingResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "角色服务未配置")
}

func (staticRoleSettingProvider) Delete(context.Context, int64) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "角色服务未配置")
}

func (staticRoleSettingProvider) Permissions(context.Context, int64) ([]platform.RolePermissionItem, error) {
	return nil, platform.NewAuthError(platform.AuthErrorInternal, "角色服务未配置")
}

func (staticRoleSettingProvider) SavePermissions(context.Context, int64, int64, []platform.RolePermissionItem) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "角色服务未配置")
}

func (staticRoleSettingProvider) PermissionOptions(context.Context) ([]platform.MenuNode, error) {
	return nil, platform.NewAuthError(platform.AuthErrorInternal, "角色服务未配置")
}

func (staticRoleSettingProvider) Templates(context.Context) ([]platform.RoleTemplate, error) {
	return nil, platform.NewAuthError(platform.AuthErrorInternal, "角色服务未配置")
}

type staticUserAccountProvider struct{}

func (staticUserAccountProvider) Page(context.Context, platform.PageQuery, string, string) (platform.PageResponse[platform.UserAccountAdminResponse], error) {
	return platform.PageResponse[platform.UserAccountAdminResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "用户账户服务未配置")
}

func (staticUserAccountProvider) Detail(context.Context, int64) (platform.UserAccountAdminResponse, error) {
	return platform.UserAccountAdminResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "用户账户服务未配置")
}

func (staticUserAccountProvider) CheckLoginNameAvailability(context.Context, string, *int64) (platform.LoginNameAvailabilityResponse, error) {
	return platform.LoginNameAvailabilityResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "用户账户服务未配置")
}

func (staticUserAccountProvider) Create(context.Context, int64, platform.UserAccountAdminRequest) (platform.UserAccountCreateResponse, error) {
	return platform.UserAccountCreateResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "用户账户服务未配置")
}

func (staticUserAccountProvider) Update(context.Context, int64, platform.UserAccountAdminRequest) (platform.UserAccountAdminResponse, error) {
	return platform.UserAccountAdminResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "用户账户服务未配置")
}

func (staticUserAccountProvider) Delete(context.Context, int64) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "用户账户服务未配置")
}

func (staticUserAccountProvider) Preferences(context.Context, int64) (platform.UserAccountPreferencesPayload, error) {
	return platform.UserAccountPreferencesPayload{}, platform.NewAuthError(platform.AuthErrorInternal, "用户账户服务未配置")
}

func (staticUserAccountProvider) SavePreferences(context.Context, int64, platform.UserAccountPreferencesPayload) (platform.UserAccountPreferencesPayload, error) {
	return platform.UserAccountPreferencesPayload{}, platform.NewAuthError(platform.AuthErrorInternal, "用户账户服务未配置")
}

func (staticUserAccountProvider) Setup2FA(context.Context, int64) (platform.TotpSetupResponse, error) {
	return platform.TotpSetupResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "用户账户服务未配置")
}

func (staticUserAccountProvider) Enable2FA(context.Context, int64, platform.TotpEnableRequest) (platform.UserAccountAdminResponse, error) {
	return platform.UserAccountAdminResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "用户账户服务未配置")
}

func (staticUserAccountProvider) Disable2FA(context.Context, int64) (platform.UserAccountAdminResponse, error) {
	return platform.UserAccountAdminResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "用户账户服务未配置")
}

type staticRefreshTokenProvider struct{}

func (staticRefreshTokenProvider) Page(context.Context, platform.PageQuery, string) (platform.PageResponse[platform.RefreshTokenAdminResponse], error) {
	return platform.PageResponse[platform.RefreshTokenAdminResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "会话服务未配置")
}

func (staticRefreshTokenProvider) Summary(context.Context) (platform.RefreshTokenSessionSummaryResponse, error) {
	return platform.RefreshTokenSessionSummaryResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "会话服务未配置")
}

func (staticRefreshTokenProvider) Revoke(context.Context, int64) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "会话服务未配置")
}

func (staticRefreshTokenProvider) RevokeAll(context.Context) (int, error) {
	return 0, platform.NewAuthError(platform.AuthErrorInternal, "会话服务未配置")
}

type staticApiKeyProvider struct{}

func (staticApiKeyProvider) Page(context.Context, int64, platform.PageQuery, string, *int64, string, string) (platform.PageResponse[platform.ApiKeyResponse], error) {
	return platform.PageResponse[platform.ApiKeyResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "API Key 服务未配置")
}

func (staticApiKeyProvider) Detail(context.Context, int64, int64) (platform.ApiKeyResponse, error) {
	return platform.ApiKeyResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "API Key 服务未配置")
}

func (staticApiKeyProvider) UserOptions(context.Context, string) ([]platform.ApiKeyUserOptionResponse, error) {
	return []platform.ApiKeyUserOptionResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "API Key 服务未配置")
}

func (staticApiKeyProvider) ResourceOptions(context.Context) ([]platform.ApiKeyResourceOptionResponse, error) {
	return []platform.ApiKeyResourceOptionResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "API Key 服务未配置")
}

func (staticApiKeyProvider) ActionOptions(context.Context) ([]platform.ApiKeyActionOptionResponse, error) {
	return []platform.ApiKeyActionOptionResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "API Key 服务未配置")
}

func (staticApiKeyProvider) Generate(context.Context, int64, int64, platform.ApiKeyRequest) (platform.ApiKeyResponse, error) {
	return platform.ApiKeyResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "API Key 服务未配置")
}

func (staticApiKeyProvider) Revoke(context.Context, int64, int64) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "API Key 服务未配置")
}

type staticPrintTemplateProvider struct{}

func (staticPrintTemplateProvider) ListByBillType(context.Context, string) ([]platform.PrintTemplateResponse, error) {
	return []platform.PrintTemplateResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "打印模板服务未配置")
}

func (staticPrintTemplateProvider) GetBillType(context.Context, int64) (string, error) {
	return "", platform.NewAuthError(platform.AuthErrorInternal, "打印模板服务未配置")
}

func (staticPrintTemplateProvider) Create(context.Context, platform.PrintTemplateRequest) (platform.PrintTemplateResponse, error) {
	return platform.PrintTemplateResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "打印模板服务未配置")
}

func (staticPrintTemplateProvider) Update(context.Context, int64, platform.PrintTemplateRequest) (platform.PrintTemplateResponse, error) {
	return platform.PrintTemplateResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "打印模板服务未配置")
}

func (staticPrintTemplateProvider) UploadJSON(context.Context, int64, string, []byte) (platform.PrintTemplateResponse, error) {
	return platform.PrintTemplateResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "打印模板服务未配置")
}

func (staticPrintTemplateProvider) Delete(context.Context, int64) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "打印模板服务未配置")
}

type staticMaterialCategoryProvider struct{}

func (staticMaterialCategoryProvider) Page(context.Context, platform.PageQuery, string, string) (platform.PageResponse[platform.MaterialCategoryResponse], error) {
	return platform.PageResponse[platform.MaterialCategoryResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "商品类别服务未配置")
}

func (staticMaterialCategoryProvider) Detail(context.Context, int64) (platform.MaterialCategoryResponse, error) {
	return platform.MaterialCategoryResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "商品类别服务未配置")
}

func (staticMaterialCategoryProvider) Create(context.Context, platform.MaterialCategoryRequest) (platform.MaterialCategoryResponse, error) {
	return platform.MaterialCategoryResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "商品类别服务未配置")
}

func (staticMaterialCategoryProvider) Update(context.Context, int64, platform.MaterialCategoryRequest) (platform.MaterialCategoryResponse, error) {
	return platform.MaterialCategoryResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "商品类别服务未配置")
}

func (staticMaterialCategoryProvider) Delete(context.Context, int64) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "商品类别服务未配置")
}

func (staticMaterialCategoryProvider) Options(context.Context) ([]platform.MaterialCategoryOptionResponse, error) {
	return nil, platform.NewAuthError(platform.AuthErrorInternal, "商品类别服务未配置")
}

type staticSupplierProvider struct{}

func (staticSupplierProvider) Page(context.Context, platform.PageQuery, string, string) (platform.PageResponse[platform.SupplierResponse], error) {
	return platform.PageResponse[platform.SupplierResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "供应商服务未配置")
}

func (staticSupplierProvider) Detail(context.Context, int64) (platform.SupplierResponse, error) {
	return platform.SupplierResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "供应商服务未配置")
}

func (staticSupplierProvider) Create(context.Context, platform.SupplierRequest) (platform.SupplierResponse, error) {
	return platform.SupplierResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "供应商服务未配置")
}

func (staticSupplierProvider) Update(context.Context, int64, platform.SupplierRequest) (platform.SupplierResponse, error) {
	return platform.SupplierResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "供应商服务未配置")
}

func (staticSupplierProvider) Delete(context.Context, int64) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "供应商服务未配置")
}

func (staticSupplierProvider) Options(context.Context) ([]platform.SupplierOptionResponse, error) {
	return nil, platform.NewAuthError(platform.AuthErrorInternal, "供应商服务未配置")
}

type staticCustomerProvider struct{}

func (staticCustomerProvider) Page(context.Context, platform.PageQuery, string, string) (platform.PageResponse[platform.CustomerResponse], error) {
	return platform.PageResponse[platform.CustomerResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "客户服务未配置")
}

func (staticCustomerProvider) Detail(context.Context, int64) (platform.CustomerResponse, error) {
	return platform.CustomerResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "客户服务未配置")
}

func (staticCustomerProvider) Create(context.Context, platform.CustomerRequest) (platform.CustomerResponse, error) {
	return platform.CustomerResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "客户服务未配置")
}

func (staticCustomerProvider) Update(context.Context, int64, platform.CustomerRequest) (platform.CustomerResponse, error) {
	return platform.CustomerResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "客户服务未配置")
}

func (staticCustomerProvider) Delete(context.Context, int64) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "客户服务未配置")
}

func (staticCustomerProvider) Options(context.Context) ([]platform.CustomerOptionResponse, error) {
	return nil, platform.NewAuthError(platform.AuthErrorInternal, "客户服务未配置")
}

type staticProjectProvider struct{}

func (staticProjectProvider) Page(context.Context, platform.PageQuery, string, string) (platform.PageResponse[platform.ProjectResponse], error) {
	return platform.PageResponse[platform.ProjectResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "项目服务未配置")
}

func (staticProjectProvider) Detail(context.Context, int64) (platform.ProjectResponse, error) {
	return platform.ProjectResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "项目服务未配置")
}

func (staticProjectProvider) Create(context.Context, platform.ProjectRequest) (platform.ProjectResponse, error) {
	return platform.ProjectResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "项目服务未配置")
}

func (staticProjectProvider) Update(context.Context, int64, platform.ProjectRequest) (platform.ProjectResponse, error) {
	return platform.ProjectResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "项目服务未配置")
}

func (staticProjectProvider) Delete(context.Context, int64) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "项目服务未配置")
}

type staticCarrierProvider struct{}

func (staticCarrierProvider) Page(context.Context, platform.PageQuery, string, string) (platform.PageResponse[platform.CarrierResponse], error) {
	return platform.PageResponse[platform.CarrierResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "物流方服务未配置")
}

func (staticCarrierProvider) Detail(context.Context, int64) (platform.CarrierResponse, error) {
	return platform.CarrierResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "物流方服务未配置")
}

func (staticCarrierProvider) Create(context.Context, platform.CarrierRequest) (platform.CarrierResponse, error) {
	return platform.CarrierResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "物流方服务未配置")
}

func (staticCarrierProvider) Update(context.Context, int64, platform.CarrierRequest) (platform.CarrierResponse, error) {
	return platform.CarrierResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "物流方服务未配置")
}

func (staticCarrierProvider) Delete(context.Context, int64) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "物流方服务未配置")
}

func (staticCarrierProvider) Options(context.Context) ([]platform.CarrierOptionResponse, error) {
	return nil, platform.NewAuthError(platform.AuthErrorInternal, "物流方服务未配置")
}

type staticWarehouseProvider struct{}

func (staticWarehouseProvider) Page(context.Context, platform.PageQuery, string, string, string) (platform.PageResponse[platform.WarehouseResponse], error) {
	return platform.PageResponse[platform.WarehouseResponse]{}, platform.NewAuthError(platform.AuthErrorInternal, "仓库服务未配置")
}

func (staticWarehouseProvider) Detail(context.Context, int64) (platform.WarehouseResponse, error) {
	return platform.WarehouseResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "仓库服务未配置")
}

func (staticWarehouseProvider) Create(context.Context, platform.WarehouseRequest) (platform.WarehouseResponse, error) {
	return platform.WarehouseResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "仓库服务未配置")
}

func (staticWarehouseProvider) Update(context.Context, int64, platform.WarehouseRequest) (platform.WarehouseResponse, error) {
	return platform.WarehouseResponse{}, platform.NewAuthError(platform.AuthErrorInternal, "仓库服务未配置")
}

func (staticWarehouseProvider) Delete(context.Context, int64) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "仓库服务未配置")
}

func (staticWarehouseProvider) Options(context.Context) ([]platform.OptionResponse, error) {
	return nil, platform.NewAuthError(platform.AuthErrorInternal, "仓库服务未配置")
}

type staticRateLimitAdminProvider struct{}

func (staticRateLimitAdminProvider) ListRules(context.Context) ([]map[string]any, error) {
	return []map[string]any{}, platform.NewAuthError(platform.AuthErrorInternal, "限流规则服务未配置")
}

func (staticRateLimitAdminProvider) UpdateRule(context.Context, int64, map[string]any) error {
	return platform.NewAuthError(platform.AuthErrorInternal, "限流规则服务未配置")
}

func writeAuthError(c *gin.Context, err error) {
	var authErr platform.AuthError
	if errors.As(err, &authErr) {
		switch authErr.Kind {
		case platform.AuthErrorValidation:
			writeFailure(c, http.StatusBadRequest, codeValidationError, authErr.Message)
		case platform.AuthErrorUnauthorized:
			writeFailure(c, http.StatusUnauthorized, codeUnauthorized, authErr.Message)
		case platform.AuthErrorForbidden:
			writeFailure(c, http.StatusForbidden, codeForbidden, authErr.Message)
		case platform.AuthErrorConflict:
			writeFailure(c, http.StatusConflict, codeRefreshTokenReuseConflict, authErr.Message)
		case platform.AuthErrorNotFound:
			writeFailure(c, http.StatusNotFound, codeNotFound, authErr.Message)
		case platform.AuthErrorBusiness:
			writeFailure(c, http.StatusUnprocessableEntity, codeBusinessError, authErr.Message)
		default:
			writeFailure(c, http.StatusInternalServerError, codeInternalError, "系统异常")
		}
		return
	}
	writeFailure(c, http.StatusInternalServerError, codeInternalError, "系统异常")
}

func writeRefreshCookie(c *gin.Context, refreshToken string, maxAge time.Duration, secure bool) {
	if maxAge <= 0 {
		maxAge = 7 * 24 * time.Hour
	}
	c.SetSameSite(http.SameSiteStrictMode)
	c.SetCookie("leo_refresh_token", refreshToken, int(maxAge.Seconds()), "/api/auth", "", secure, true)
}

func clearRefreshCookie(c *gin.Context, secure bool) {
	c.SetSameSite(http.SameSiteStrictMode)
	c.SetCookie("leo_refresh_token", "", -1, "/api/auth", "", secure, true)
}

func resolveRefreshToken(c *gin.Context, fallback string) string {
	if value, err := c.Cookie("leo_refresh_token"); err == nil && strings.TrimSpace(value) != "" {
		return value
	}
	return strings.TrimSpace(fallback)
}

func resolveTotpCode(c *gin.Context, fallback string) string {
	if value := strings.TrimSpace(c.GetHeader("X-TOTP-Code")); value != "" {
		return value
	}
	return strings.TrimSpace(fallback)
}

func pageQuery(c *gin.Context) platform.PageQuery {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "0"))
	size, _ := strconv.Atoi(c.DefaultQuery("size", "20"))
	return platform.NormalizePageQuery(page, size, c.Query("sortBy"), c.Query("direction"))
}

func strictPageQuery(c *gin.Context, allowedSortFields map[string]struct{}) (platform.PageQuery, bool) {
	return strictPageQueryWithDirectionParam(c, "direction", allowedSortFields)
}

func strictPageQueryWithDirectionParam(c *gin.Context, directionParam string, allowedSortFields map[string]struct{}) (platform.PageQuery, bool) {
	page, ok := strictOptionalIntQuery(c, "page", 0)
	if !ok {
		return platform.PageQuery{}, false
	}
	size, ok := strictOptionalIntQuery(c, "size", 20)
	if !ok {
		return platform.PageQuery{}, false
	}
	if page < 0 {
		writeAuthError(c, platform.NewAuthError(platform.AuthErrorValidation, "page 不能小于0"))
		return platform.PageQuery{}, false
	}
	if size < 1 || size > 200 {
		writeAuthError(c, platform.NewAuthError(platform.AuthErrorValidation, "size 必须在1到200之间"))
		return platform.PageQuery{}, false
	}
	sortBy := strings.TrimSpace(c.Query("sortBy"))
	if sortBy != "" {
		if !pageSortByPattern.MatchString(sortBy) {
			writeAuthError(c, platform.NewAuthError(platform.AuthErrorValidation, "sortBy 格式不合法"))
			return platform.PageQuery{}, false
		}
		if allowedSortFields != nil {
			if _, ok := allowedSortFields[sortBy]; !ok {
				writeAuthError(c, platform.NewAuthError(platform.AuthErrorValidation, "sortBy 不支持当前列表"))
				return platform.PageQuery{}, false
			}
		}
	}
	directionParam = strings.TrimSpace(directionParam)
	if directionParam == "" {
		directionParam = "direction"
	}
	direction := strings.TrimSpace(c.Query(directionParam))
	if direction != "" {
		normalized := strings.ToLower(direction)
		if normalized != "asc" && normalized != "desc" {
			writeAuthError(c, platform.NewAuthError(platform.AuthErrorValidation, directionParam+" 只能为 asc 或 desc"))
			return platform.PageQuery{}, false
		}
		direction = normalized
	}
	if direction == "" {
		direction = "desc"
	}
	return platform.PageQuery{Page: page, Size: size, SortBy: sortBy, Direction: direction}, true
}

func strictOptionalIntQuery(c *gin.Context, name string, fallback int) (int, bool) {
	raw := strings.TrimSpace(c.Query(name))
	if raw == "" {
		return fallback, true
	}
	value, err := strconv.Atoi(raw)
	if err != nil {
		writeAuthError(c, platform.NewAuthError(platform.AuthErrorValidation, name+": 参数格式错误"))
		return 0, false
	}
	return value, true
}

func pathID(c *gin.Context) (int64, bool) {
	id, err := strconv.ParseInt(strings.TrimSpace(c.Param("id")), 10, 64)
	if err != nil || id <= 0 {
		writeFailure(c, http.StatusBadRequest, codeValidationError, "id必须为正整数")
		return 0, false
	}
	return id, true
}

func optionalInt64Query(c *gin.Context, name string) (*int64, bool) {
	raw := strings.TrimSpace(c.Query(name))
	if raw == "" {
		return nil, true
	}
	value, err := strconv.ParseInt(raw, 10, 64)
	if err != nil || value <= 0 {
		writeFailure(c, http.StatusBadRequest, codeValidationError, name+"必须为正整数")
		return nil, false
	}
	return &value, true
}

func clientIP(c *gin.Context) string {
	for _, header := range []string{"X-Forwarded-For", "X-Real-IP"} {
		value := strings.TrimSpace(c.GetHeader(header))
		if value == "" {
			continue
		}
		if index := strings.Index(value, ","); index >= 0 {
			value = strings.TrimSpace(value[:index])
		}
		if value != "" {
			return value
		}
	}
	if ip := strings.TrimSpace(c.ClientIP()); ip != "" {
		return ip
	}
	return "unknown"
}
