package httpapi

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/leo-erp/leo/internal/config"
	"github.com/leo-erp/leo/internal/platform"
)

func TestHealthEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := NewRouter(
		config.Config{AppName: "leo", Database: config.DatabaseConfig{ValidationTimeout: time.Second}},
		slog.New(slog.DiscardHandler),
		fakeHealthChecker{health: platform.Health{
			DB:    platform.HealthCheck{Status: "UP"},
			Redis: platform.HealthCheck{Status: "UP"},
			Disk:  platform.HealthCheck{Status: "UP", FreeGB: 10, TotalGB: 20},
		}},
		fakeSetupStatusProvider{},
		fakeCaptchaGenerator{},
		fakeAuthProvider{},
	)

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodGet, "/api/health", nil)
	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}

	var body struct {
		Code int `json:"code"`
		Data struct {
			Status string `json:"status"`
			App    string `json:"app"`
			Disk   struct {
				Status string `json:"status"`
			} `json:"disk"`
		} `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Code != 0 {
		t.Fatalf("code = %d, want 0", body.Code)
	}
	if body.Data.App != "leo" {
		t.Fatalf("app = %q, want leo", body.Data.App)
	}
	if body.Data.Status == "" || body.Data.Disk.Status == "" {
		t.Fatalf("health status should be populated: %+v", body.Data)
	}
}

func TestSetupStatusEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := NewRouter(config.Config{
		AppName:           "leo",
		Database:          config.DatabaseConfig{ValidationTimeout: time.Second},
		SetupRequired:     true,
		AdminConfigured:   false,
		CompanyConfigured: true,
	}, slog.New(slog.DiscardHandler), fakeHealthChecker{}, fakeSetupStatusProvider{
		status: platform.SetupStatus{
			SetupRequired:     false,
			AdminConfigured:   true,
			CompanyConfigured: true,
		},
	}, fakeCaptchaGenerator{}, fakeAuthProvider{})

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodGet, "/api/setup/status", nil)
	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}

	var body struct {
		Message string `json:"message"`
		Data    struct {
			SetupRequired     bool `json:"setupRequired"`
			AdminConfigured   bool `json:"adminConfigured"`
			CompanyConfigured bool `json:"companyConfigured"`
		} `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Message != "获取初始化状态成功" {
		t.Fatalf("message = %q", body.Message)
	}
	if body.Data.SetupRequired || !body.Data.AdminConfigured || !body.Data.CompanyConfigured {
		t.Fatalf("unexpected setup status: %+v", body.Data)
	}
}

func TestSetupStatusEndpointReturnsApiErrorOnProviderFailure(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := NewRouter(
		config.Config{AppName: "leo", Database: config.DatabaseConfig{ValidationTimeout: time.Second}},
		slog.New(slog.DiscardHandler),
		fakeHealthChecker{},
		fakeSetupStatusProvider{err: errors.New("database unavailable")},
		fakeCaptchaGenerator{},
		fakeAuthProvider{},
	)

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodGet, "/api/setup/status", nil)
	router.ServeHTTP(response, request)

	if response.Code != http.StatusInternalServerError {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusInternalServerError)
	}

	var body struct {
		Code int `json:"code"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Code != codeInternalError.Code {
		t.Fatalf("code = %d, want %d", body.Code, codeInternalError.Code)
	}
}

func TestSetupInitializeEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	tests := []struct {
		name             string
		initializer      fakeSetupInitializer
		body             string
		wantStatus       int
		wantCode         int
		wantMsg          string
		wantAdmin        string
		wantCompany      string
		wantAdminRequest bool
		wantCompanyReq   bool
	}{
		{
			name: "initialize new environment",
			initializer: fakeSetupInitializer{
				initializeResponse: platform.InitialSetupSubmitResponse{
					AdminLoginName: "admin",
					CompanyName:    "Leo Trading",
				},
			},
			body: `{
				"admin":{"admin":{"loginName":"admin","password":"secret123","userName":"管理员","mobile":"13800138000"},"totpCode":"123456"},
				"company":{"companyName":"Leo Trading","taxNo":"TAX123","bankName":"Bank","bankAccount":"6222","taxRate":0.13,"remark":"init"}
			}`,
			wantStatus:       http.StatusOK,
			wantCode:         codeSuccess.Code,
			wantMsg:          "系统首次初始化完成",
			wantAdmin:        "admin",
			wantCompany:      "Leo Trading",
			wantAdminRequest: true,
			wantCompanyReq:   true,
		},
		{
			name: "complete missing admin only",
			initializer: fakeSetupInitializer{
				initializeResponse: platform.InitialSetupSubmitResponse{
					AdminLoginName: "admin",
					CompanyName:    "Existing Co",
				},
			},
			body: `{
				"admin":{"admin":{"loginName":"admin","password":"secret123","userName":"管理员","mobile":"13800138000"},"totpCode":"123456"}
			}`,
			wantStatus:       http.StatusOK,
			wantCode:         codeSuccess.Code,
			wantMsg:          "系统首次初始化完成",
			wantAdmin:        "admin",
			wantCompany:      "Existing Co",
			wantAdminRequest: true,
			wantCompanyReq:   false,
		},
		{
			name: "complete missing company only",
			initializer: fakeSetupInitializer{
				initializeResponse: platform.InitialSetupSubmitResponse{
					AdminLoginName: "existing-admin",
					CompanyName:    "Leo Trading",
				},
			},
			body: `{
				"company":{"companyName":"Leo Trading","taxNo":"TAX123","bankName":"Bank","bankAccount":"6222","taxRate":0.13,"remark":"init"}
			}`,
			wantStatus:       http.StatusOK,
			wantCode:         codeSuccess.Code,
			wantMsg:          "系统首次初始化完成",
			wantAdmin:        "existing-admin",
			wantCompany:      "Leo Trading",
			wantAdminRequest: false,
			wantCompanyReq:   true,
		},
		{
			name: "reject when already completed",
			initializer: fakeSetupInitializer{
				initializeErr: platform.NewAuthError(platform.AuthErrorBusiness, "系统已完成首次初始化"),
			},
			body:       `{}`,
			wantStatus: http.StatusUnprocessableEntity,
			wantCode:   codeBusinessError.Code,
			wantMsg:    "系统已完成首次初始化",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			initializer := tt.initializer
			router := newServicesTestRouter(fakeAuthProvider{}, RouterServices{
				SetupInitializer: &initializer,
			})
			response := httptest.NewRecorder()
			request := httptest.NewRequest(http.MethodPost, "/api/setup/initialize", bytes.NewBufferString(tt.body))
			request.Header.Set("Content-Type", "application/json")
			router.ServeHTTP(response, request)

			assertAPIResponse(t, response, tt.wantStatus, tt.wantCode, tt.wantMsg)
			if tt.wantStatus != http.StatusOK {
				return
			}
			var body struct {
				Data struct {
					AdminLoginName string `json:"adminLoginName"`
					CompanyName    string `json:"companyName"`
				} `json:"data"`
			}
			if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
				t.Fatalf("decode response: %v", err)
			}
			if body.Data.AdminLoginName != tt.wantAdmin || body.Data.CompanyName != tt.wantCompany {
				t.Fatalf("unexpected initialize response: %+v", body.Data)
			}
			if initializer.initializeRequest.Admin != nil != tt.wantAdminRequest {
				t.Fatalf("admin request presence = %v, want %v", initializer.initializeRequest.Admin != nil, tt.wantAdminRequest)
			}
			if initializer.initializeRequest.Company != nil != tt.wantCompanyReq {
				t.Fatalf("company request presence = %v, want %v", initializer.initializeRequest.Company != nil, tt.wantCompanyReq)
			}
		})
	}
}

func TestCaptchaEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := NewRouter(
		config.Config{AppName: "leo", Database: config.DatabaseConfig{ValidationTimeout: time.Second}, Redis: config.RedisConfig{Timeout: time.Second}},
		slog.New(slog.DiscardHandler),
		fakeHealthChecker{},
		fakeSetupStatusProvider{},
		fakeCaptchaGenerator{captcha: platform.Captcha{
			CaptchaID:    "captcha-id",
			CaptchaImage: "data:image/png;base64,abc",
			Required:     true,
		}},
		fakeAuthProvider{},
	)

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodGet, "/api/auth/captcha", nil)
	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}

	var body struct {
		Code int `json:"code"`
		Data struct {
			CaptchaID    string `json:"captchaId"`
			CaptchaImage string `json:"captchaImage"`
			Required     bool   `json:"required"`
		} `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Code != 0 {
		t.Fatalf("code = %d, want 0", body.Code)
	}
	if body.Data.CaptchaID != "captcha-id" || body.Data.CaptchaImage == "" || !body.Data.Required {
		t.Fatalf("unexpected captcha response: %+v", body.Data)
	}
}

func TestCaptchaEndpointReturnsApiErrorOnGeneratorFailure(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := NewRouter(
		config.Config{AppName: "leo", Database: config.DatabaseConfig{ValidationTimeout: time.Second}, Redis: config.RedisConfig{Timeout: time.Second}},
		slog.New(slog.DiscardHandler),
		fakeHealthChecker{},
		fakeSetupStatusProvider{},
		fakeCaptchaGenerator{err: errors.New("redis unavailable")},
		fakeAuthProvider{},
	)

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodGet, "/api/auth/captcha", nil)
	router.ServeHTTP(response, request)

	if response.Code != http.StatusInternalServerError {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusInternalServerError)
	}
}

func TestLoginEndpointWritesRefreshCookieAndHidesRefreshToken(t *testing.T) {
	gin.SetMode(gin.TestMode)
	refreshToken := "refresh-token"
	router := NewRouter(
		config.Config{
			AppName:  "leo",
			Database: config.DatabaseConfig{ValidationTimeout: time.Second},
			JWT:      config.JWTConfig{RefreshExpiration: 7 * 24 * time.Hour},
		},
		slog.New(slog.DiscardHandler),
		fakeHealthChecker{},
		fakeSetupStatusProvider{},
		fakeCaptchaGenerator{},
		fakeAuthProvider{loginResponse: platform.LoginResponseBody{
			AccessToken:      "access-token",
			RefreshToken:     &refreshToken,
			TokenType:        "Bearer",
			ExpiresIn:        600,
			RefreshExpiresIn: 604800,
			User:             &platform.AuthUserResponse{ID: 1, LoginName: "admin"},
		}},
	)

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodPost, "/api/auth/login", bytes.NewBufferString(`{"loginName":"admin","password":"secret"}`))
	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}
	if cookie := response.Result().Cookies()[0]; cookie.Name != "leo_refresh_token" || cookie.Value != "refresh-token" || !cookie.HttpOnly {
		t.Fatalf("unexpected refresh cookie: %+v", cookie)
	}
	var body struct {
		Data struct {
			AccessToken  string  `json:"accessToken"`
			RefreshToken *string `json:"refreshToken"`
		} `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Data.AccessToken != "access-token" || body.Data.RefreshToken != nil {
		t.Fatalf("unexpected login response: %+v", body.Data)
	}
}

func TestLoginEndpointReturnsStep1ForTwoFactor(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := NewRouter(
		config.Config{AppName: "leo", Database: config.DatabaseConfig{ValidationTimeout: time.Second}},
		slog.New(slog.DiscardHandler),
		fakeHealthChecker{},
		fakeSetupStatusProvider{},
		fakeCaptchaGenerator{},
		fakeAuthProvider{loginResponse: platform.LoginResponseBody{Requires2FA: true, TempToken: "1.temp"}},
	)

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodPost, "/api/auth/login", bytes.NewBufferString(`{"loginName":"admin","password":"secret"}`))
	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}
	if cookies := response.Result().Cookies(); len(cookies) != 0 {
		t.Fatalf("step1 should not write refresh cookie: %+v", cookies)
	}
	var body struct {
		Data struct {
			Requires2FA bool   `json:"requires2fa"`
			TempToken   string `json:"tempToken"`
		} `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if !body.Data.Requires2FA || body.Data.TempToken != "1.temp" {
		t.Fatalf("unexpected 2fa response: %+v", body.Data)
	}
}

func TestLoginEndpointMapsUnauthorizedError(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := NewRouter(
		config.Config{AppName: "leo", Database: config.DatabaseConfig{ValidationTimeout: time.Second}},
		slog.New(slog.DiscardHandler),
		fakeHealthChecker{},
		fakeSetupStatusProvider{},
		fakeCaptchaGenerator{},
		fakeAuthProvider{loginErr: platform.NewAuthError(platform.AuthErrorUnauthorized, "账号或密码错误")},
	)

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodPost, "/api/auth/login", bytes.NewBufferString(`{"loginName":"admin","password":"bad"}`))
	router.ServeHTTP(response, request)

	if response.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusUnauthorized)
	}
	var body struct {
		Code    int    `json:"code"`
		Message string `json:"message"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Code != codeUnauthorized.Code || body.Message != "账号或密码错误" {
		t.Fatalf("unexpected auth error: %+v", body)
	}
}

func TestRefreshEndpointUsesCookieAndRotatesCookie(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := NewRouter(
		config.Config{
			AppName:  "leo",
			Database: config.DatabaseConfig{ValidationTimeout: time.Second},
			JWT:      config.JWTConfig{RefreshExpiration: 7 * 24 * time.Hour},
		},
		slog.New(slog.DiscardHandler),
		fakeHealthChecker{},
		fakeSetupStatusProvider{},
		fakeCaptchaGenerator{},
		fakeAuthProvider{refreshResponse: platform.TokenResponse{
			AccessToken:      "new-access",
			RefreshToken:     "new-refresh",
			TokenType:        "Bearer",
			ExpiresIn:        600,
			RefreshExpiresIn: 604800,
			User:             platform.AuthUserResponse{ID: 1, LoginName: "admin"},
		}},
	)

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodPost, "/api/auth/refresh", bytes.NewBufferString(`{}`))
	request.AddCookie(&http.Cookie{Name: "leo_refresh_token", Value: "old-refresh"})
	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}
	if cookie := response.Result().Cookies()[0]; cookie.Name != "leo_refresh_token" || cookie.Value != "new-refresh" {
		t.Fatalf("unexpected rotated cookie: %+v", cookie)
	}
}

func TestLogoutEndpointClearsRefreshCookie(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := NewRouter(
		config.Config{AppName: "leo", Database: config.DatabaseConfig{ValidationTimeout: time.Second}},
		slog.New(slog.DiscardHandler),
		fakeHealthChecker{},
		fakeSetupStatusProvider{},
		fakeCaptchaGenerator{},
		fakeAuthProvider{},
	)

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodPost, "/api/auth/logout", bytes.NewBufferString(`{}`))
	request.AddCookie(&http.Cookie{Name: "leo_refresh_token", Value: "refresh-token"})
	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}
	if cookie := response.Result().Cookies()[0]; cookie.Name != "leo_refresh_token" || cookie.MaxAge >= 0 {
		t.Fatalf("unexpected cleared cookie: %+v", cookie)
	}
}

func TestAccountSecurityStatusEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := NewRouter(
		config.Config{AppName: "leo", Database: config.DatabaseConfig{ValidationTimeout: time.Second}},
		slog.New(slog.DiscardHandler),
		fakeHealthChecker{},
		fakeSetupStatusProvider{},
		fakeCaptchaGenerator{},
		fakeAuthProvider{
			principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
			securityStatus: platform.CurrentUserSecurityResponse{
				ID:               1,
				LoginName:        "admin",
				UserName:         "管理员",
				TotpEnabled:      true,
				ForceTotpSetup:   false,
				ForbidDisable2FA: true,
			},
		},
	)

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodGet, "/api/account/security", nil)
	request.Header.Set("Authorization", "Bearer access-token")
	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}
	var body struct {
		Data struct {
			ID               int64  `json:"id"`
			LoginName        string `json:"loginName"`
			UserName         string `json:"userName"`
			TotpEnabled      bool   `json:"totpEnabled"`
			ForceTotpSetup   bool   `json:"forceTotpSetup"`
			ForbidDisable2FA bool   `json:"forbidDisable2fa"`
		} `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Data.ID != 1 || body.Data.LoginName != "admin" || !body.Data.TotpEnabled || !body.Data.ForbidDisable2FA {
		t.Fatalf("unexpected security status: %+v", body.Data)
	}
}

func TestAccountSecurityChangePasswordEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	tests := []struct {
		name       string
		body       string
		authErr    error
		wantStatus int
		wantCode   int
		wantMsg    string
	}{
		{
			name:       "success",
			body:       `{"currentPassword":"old-secret","newPassword":"new-secret"}`,
			wantStatus: http.StatusOK,
			wantCode:   codeSuccess.Code,
			wantMsg:    "密码修改成功",
		},
		{
			name:       "bad current password maps to business error",
			body:       `{"currentPassword":"bad-secret","newPassword":"new-secret"}`,
			authErr:    platform.NewAuthError(platform.AuthErrorBusiness, "当前密码错误"),
			wantStatus: http.StatusUnprocessableEntity,
			wantCode:   codeBusinessError.Code,
			wantMsg:    "当前密码错误",
		},
		{
			name:       "same password maps to validation error",
			body:       `{"currentPassword":"old-secret","newPassword":"old-secret"}`,
			authErr:    platform.NewAuthError(platform.AuthErrorValidation, "新密码不能与当前密码相同"),
			wantStatus: http.StatusBadRequest,
			wantCode:   codeValidationError.Code,
			wantMsg:    "新密码不能与当前密码相同",
		},
		{
			name:       "missing user maps to not found",
			body:       `{"currentPassword":"old-secret","newPassword":"new-secret"}`,
			authErr:    platform.NewAuthError(platform.AuthErrorNotFound, "用户不存在"),
			wantStatus: http.StatusNotFound,
			wantCode:   codeNotFound.Code,
			wantMsg:    "用户不存在",
		},
		{
			name:       "missing body maps to validation error",
			body:       `{`,
			wantStatus: http.StatusBadRequest,
			wantCode:   codeValidationError.Code,
			wantMsg:    "请求体格式错误，请检查JSON格式",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			router := newAccountSecurityTestRouter(fakeAuthProvider{
				principal:         platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
				changePasswordErr: tt.authErr,
			})

			response := httptest.NewRecorder()
			request := authenticatedJSONRequest(http.MethodPost, "/api/account/security/password", tt.body)
			router.ServeHTTP(response, request)

			assertAPIResponse(t, response, tt.wantStatus, tt.wantCode, tt.wantMsg)
		})
	}
}

func TestAccountSecurityChangePasswordEndpointRequiresAuthentication(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := newAccountSecurityTestRouter(fakeAuthProvider{
		authenticateErr: platform.NewAuthError(platform.AuthErrorUnauthorized, "未登录"),
	})

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodPost, "/api/account/security/password", bytes.NewBufferString(`{"currentPassword":"old-secret","newPassword":"new-secret"}`))
	router.ServeHTTP(response, request)

	assertAPIResponse(t, response, http.StatusUnauthorized, codeUnauthorized.Code, "未登录")
}

func TestAccountSecuritySetup2FAEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	tests := []struct {
		name       string
		authErr    error
		wantStatus int
		wantCode   int
		wantMsg    string
	}{
		{
			name:       "success",
			wantStatus: http.StatusOK,
			wantCode:   codeSuccess.Code,
			wantMsg:    "密钥生成成功",
		},
		{
			name:       "already enabled maps to business error",
			authErr:    platform.NewAuthError(platform.AuthErrorBusiness, "2FA 已启用，请先关闭 2FA 后再重新绑定"),
			wantStatus: http.StatusUnprocessableEntity,
			wantCode:   codeBusinessError.Code,
			wantMsg:    "2FA 已启用，请先关闭 2FA 后再重新绑定",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			router := newAccountSecurityTestRouter(fakeAuthProvider{
				principal:   platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
				setup2FA:    accountSecuritySetupResponse{QRCodeBase64: "base64-png", Secret: "JBSWY3DPEHPK3PXP"},
				setup2FAErr: tt.authErr,
			})

			response := httptest.NewRecorder()
			request := authenticatedJSONRequest(http.MethodPost, "/api/account/security/2fa/setup", `{}`)
			router.ServeHTTP(response, request)

			assertAPIResponse(t, response, tt.wantStatus, tt.wantCode, tt.wantMsg)
			if tt.wantStatus != http.StatusOK {
				return
			}
			var body struct {
				Data struct {
					QRCodeBase64 string `json:"qrCodeBase64"`
					Secret       string `json:"secret"`
				} `json:"data"`
			}
			if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
				t.Fatalf("decode response: %v", err)
			}
			if body.Data.QRCodeBase64 != "base64-png" || body.Data.Secret != "JBSWY3DPEHPK3PXP" {
				t.Fatalf("unexpected setup response: %+v", body.Data)
			}
		})
	}
}

func TestAccountSecurityEnable2FAEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	tests := []struct {
		name       string
		body       string
		authErr    error
		wantStatus int
		wantCode   int
		wantMsg    string
	}{
		{
			name:       "success",
			body:       `{"totpCode":"123456"}`,
			wantStatus: http.StatusOK,
			wantCode:   codeSuccess.Code,
			wantMsg:    "2FA已启用",
		},
		{
			name:       "missing setup maps to business error",
			body:       `{"totpCode":"123456"}`,
			authErr:    platform.NewAuthError(platform.AuthErrorBusiness, "请先生成2FA密钥"),
			wantStatus: http.StatusUnprocessableEntity,
			wantCode:   codeBusinessError.Code,
			wantMsg:    "请先生成2FA密钥",
		},
		{
			name:       "invalid code maps to business error",
			body:       `{"totpCode":"000000"}`,
			authErr:    platform.NewAuthError(platform.AuthErrorBusiness, "验证码错误或已过期"),
			wantStatus: http.StatusUnprocessableEntity,
			wantCode:   codeBusinessError.Code,
			wantMsg:    "验证码错误或已过期",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			router := newAccountSecurityTestRouter(fakeAuthProvider{
				principal:    platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
				enable2FA:    accountSecurityStatus(true),
				enable2FAErr: tt.authErr,
			})

			response := httptest.NewRecorder()
			request := authenticatedJSONRequest(http.MethodPost, "/api/account/security/2fa/enable", tt.body)
			router.ServeHTTP(response, request)

			assertAPIResponse(t, response, tt.wantStatus, tt.wantCode, tt.wantMsg)
			if tt.wantStatus == http.StatusOK {
				assertSecurityStatusData(t, response, true)
			}
		})
	}
}

func TestAccountSecurityDisable2FAEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	tests := []struct {
		name       string
		authErr    error
		wantStatus int
		wantCode   int
		wantMsg    string
	}{
		{
			name:       "success",
			wantStatus: http.StatusOK,
			wantCode:   codeSuccess.Code,
			wantMsg:    "2FA已禁用",
		},
		{
			name:       "system switch forbids disabling maps to business error",
			authErr:    platform.NewAuthError(platform.AuthErrorBusiness, "系统设置禁止关闭 2FA，请联系管理员"),
			wantStatus: http.StatusUnprocessableEntity,
			wantCode:   codeBusinessError.Code,
			wantMsg:    "系统设置禁止关闭 2FA，请联系管理员",
		},
		{
			name:       "totp verification failed",
			authErr:    platform.NewAuthError(platform.AuthErrorBusiness, "2FA验证码错误或已过期"),
			wantStatus: http.StatusUnprocessableEntity,
			wantCode:   codeBusinessError.Code,
			wantMsg:    "2FA验证码错误或已过期",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			router := newAccountSecurityTestRouter(fakeAuthProvider{
				principal:     platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin", TotpEnabled: true},
				disable2FA:    accountSecurityStatus(false),
				disable2FAErr: tt.authErr,
			})

			response := httptest.NewRecorder()
			request := authenticatedJSONRequest(http.MethodPost, "/api/account/security/2fa/disable", `{}`)
			request.Header.Set("X-Totp-Code", "123456")
			router.ServeHTTP(response, request)

			assertAPIResponse(t, response, tt.wantStatus, tt.wantCode, tt.wantMsg)
			if tt.wantStatus == http.StatusOK {
				assertSecurityStatusData(t, response, false)
			}
		})
	}
}

func TestMetaCodeEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := NewRouter(
		config.Config{AppName: "leo", Database: config.DatabaseConfig{ValidationTimeout: time.Second}},
		slog.New(slog.DiscardHandler),
		fakeHealthChecker{},
		fakeSetupStatusProvider{},
		fakeCaptchaGenerator{},
		fakeAuthProvider{},
	)

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodGet, "/api/meta/code", nil)
	router.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}

	var body struct {
		Data struct {
			ErrorCodes     []ErrorCode       `json:"errorCodes"`
			ResourceLabels map[string]string `json:"resourceLabels"`
			ActionLabels   map[string]string `json:"actionLabels"`
		} `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if len(body.Data.ErrorCodes) == 0 {
		t.Fatal("error codes should not be empty")
	}
	if body.Data.ResourceLabels["sales-order"] != "销售订单" {
		t.Fatalf("sales-order label = %q", body.Data.ResourceLabels["sales-order"])
	}
	if body.Data.ActionLabels["manage_permissions"] != "配置权限" {
		t.Fatalf("manage_permissions label = %q", body.Data.ActionLabels["manage_permissions"])
	}
}

func TestDashboardSummaryEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	now := time.Date(2026, 6, 18, 9, 30, 0, 0, time.UTC)
	companyName := "Leo Trading"
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission: fakePermissionChecker{},
		Dashboard: fakeDashboardProvider{summary: platform.DashboardSummary{
			AppName:            "leo",
			CompanyName:        &companyName,
			UserName:           "管理员",
			LoginName:          "admin",
			RoleName:           "系统管理员",
			VisibleMenuCount:   3,
			ModuleCount:        2,
			ActionCount:        5,
			ActiveSessionCount: 1,
			TotpEnabled:        true,
			ServerTime:         now,
			MaterialCount:      10,
			SupplierCount:      20,
			CustomerCount:      30,
		}},
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/dashboard/summary", "")
	router.ServeHTTP(response, request)

	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var body struct {
		Data struct {
			AppName            string `json:"appName"`
			CompanyName        string `json:"companyName"`
			VisibleMenuCount   int64  `json:"visibleMenuCount"`
			ActiveSessionCount int64  `json:"activeSessionCount"`
			TotpEnabled        bool   `json:"totpEnabled"`
		} `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Data.AppName != "leo" || body.Data.CompanyName != companyName || body.Data.VisibleMenuCount != 3 || !body.Data.TotpEnabled {
		t.Fatalf("unexpected dashboard summary: %+v", body.Data)
	}
}

func TestDashboardSummaryEndpointRequiresPermission(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission: fakePermissionChecker{err: platform.NewAuthError(platform.AuthErrorForbidden, "无权访问")},
		Dashboard:  fakeDashboardProvider{},
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/dashboard/summary", "")
	router.ServeHTTP(response, request)

	assertAPIResponse(t, response, http.StatusForbidden, codeForbidden.Code, "无权访问")
}

func TestMenuTreeEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	parentCode := "system"
	routePath := "/dashboard"
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Menu: fakeMenuProvider{tree: []platform.MenuNode{{
			MenuCode:   "dashboard",
			MenuName:   "工作台",
			ParentCode: &parentCode,
			RoutePath:  &routePath,
			SortOrder:  1,
			MenuType:   "菜单",
			Actions:    []string{"read"},
			Children:   []platform.MenuNode{},
		}}},
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/system/menu/tree", "")
	router.ServeHTTP(response, request)

	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var body struct {
		Data []struct {
			MenuCode string   `json:"menuCode"`
			Actions  []string `json:"actions"`
		} `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if len(body.Data) != 1 || body.Data[0].MenuCode != "dashboard" || len(body.Data[0].Actions) != 1 {
		t.Fatalf("unexpected menu tree: %+v", body.Data)
	}
}

func TestPermissionCatalogEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{Permission: fakePermissionChecker{}})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/permissions/catalog", "")
	router.ServeHTTP(response, request)

	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var body struct {
		Data []struct {
			Code    string `json:"code"`
			Actions []struct {
				Code string `json:"code"`
			} `json:"actions"`
		} `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	foundDashboardRead := false
	for _, entry := range body.Data {
		if entry.Code != "dashboard" {
			continue
		}
		for _, action := range entry.Actions {
			if action.Code == "read" {
				foundDashboardRead = true
			}
		}
	}
	if !foundDashboardRead {
		t.Fatalf("dashboard/read should be present in catalog: %+v", body.Data)
	}
}

func TestPermissionCrudEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	permission := platform.PermissionEntryResponse{
		ID:             1,
		PermissionCode: "dashboard:read",
		PermissionName: "工作台查看",
		ModuleName:     "工作台",
		Status:         "正常",
	}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission:      fakePermissionChecker{},
		PermissionEntry: fakePermissionEntryProvider{page: platform.NewPageResponse([]platform.PermissionEntryResponse{permission}, 1, platform.NormalizePageQuery(0, 20, "", "")), detail: permission},
	})

	for _, target := range []string{"/api/permissions", "/api/permissions/1"} {
		response := httptest.NewRecorder()
		request := authenticatedJSONRequest(http.MethodGet, target, "")
		router.ServeHTTP(response, request)
		assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	}
}

func TestCompanyCurrentEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission: fakePermissionChecker{},
		Company: fakeCompanyProvider{
			current: &platform.CompanySetting{
				ID:          1,
				CompanyName: "Leo Trading",
				TaxNo:       "TAX123",
				TaxRate:     0.13,
				Status:      "正常",
				SettlementAccounts: []platform.CompanySettlementAccount{{
					AccountName: "Leo Trading",
					BankName:    "Bank",
					BankAccount: "6222",
					UsageType:   "通用",
					Status:      "正常",
				}},
			},
			saved: platform.CompanySetting{ID: 1, CompanyName: "Leo Trading", TaxNo: "TAX123", Status: "正常"},
		},
	})

	getResponse := httptest.NewRecorder()
	getRequest := authenticatedJSONRequest(http.MethodGet, "/api/company-settings/current", "")
	router.ServeHTTP(getResponse, getRequest)

	assertAPIResponse(t, getResponse, http.StatusOK, codeSuccess.Code, codeSuccess.Message)

	putResponse := httptest.NewRecorder()
	putRequest := authenticatedJSONRequest(http.MethodPut, "/api/company-settings/current", `{
		"companyName":"Leo Trading",
		"taxNo":"TAX123",
		"settlementAccounts":[{"accountName":"Leo Trading","bankName":"Bank","bankAccount":"6222","usageType":"通用","status":"正常"}],
		"status":"正常"
	}`)
	router.ServeHTTP(putResponse, putRequest)

	assertAPIResponse(t, putResponse, http.StatusOK, codeSuccess.Code, "保存成功")
}

func TestCompanyCrudEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	company := platform.CompanySetting{ID: 1, CompanyName: "Leo Trading", TaxNo: "TAX123", Status: "正常"}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission: fakePermissionChecker{},
		Company: fakeCompanyProvider{
			page:    platform.NewPageResponse([]platform.CompanySetting{company}, 1, platform.NormalizePageQuery(0, 20, "", "")),
			detail:  company,
			created: company,
			updated: company,
		},
	})

	tests := []struct {
		method string
		target string
		body   string
		msg    string
	}{
		{method: http.MethodGet, target: "/api/company-settings", msg: codeSuccess.Message},
		{method: http.MethodGet, target: "/api/company-settings/1", msg: codeSuccess.Message},
		{method: http.MethodPost, target: "/api/company-settings", body: `{"companyName":"Leo Trading","taxNo":"TAX123","settlementAccounts":[{"accountName":"Leo Trading","bankName":"Bank","bankAccount":"6222","usageType":"通用","status":"正常"}],"status":"正常"}`, msg: "创建成功"},
		{method: http.MethodPut, target: "/api/company-settings/1", body: `{"companyName":"Leo Trading","taxNo":"TAX123","settlementAccounts":[{"accountName":"Leo Trading","bankName":"Bank","bankAccount":"6222","usageType":"通用","status":"正常"}],"status":"正常"}`, msg: "更新成功"},
		{method: http.MethodDelete, target: "/api/company-settings/1", msg: "删除成功"},
	}
	for _, tt := range tests {
		response := httptest.NewRecorder()
		request := authenticatedJSONRequest(tt.method, tt.target, tt.body)
		router.ServeHTTP(response, request)
		assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, tt.msg)
	}
}

func TestCompanyNameEndpointIsPublic(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := newServicesTestRouter(fakeAuthProvider{}, RouterServices{
		Company: fakeCompanyProvider{current: &platform.CompanySetting{CompanyName: "Leo Trading"}},
	})

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodGet, "/api/company-settings/name", nil)
	router.ServeHTTP(response, request)

	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var body struct {
		Data string `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Data != "Leo Trading" {
		t.Fatalf("company name = %q", body.Data)
	}
}

func TestDatabaseEndpointsReturnSuccessEnvelope(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission: fakePermissionChecker{},
		Database: fakeDatabaseProvider{
			status: platform.DatabaseStatus{
				Postgres: platform.PostgresStatus{Status: "正常"},
				Redis:    platform.RedisStatus{Status: "异常: redis unavailable"},
			},
			monitoring: platform.DatabaseMonitoring{
				Available:   false,
				Status:      "异常: database unavailable",
				TableHealth: []platform.TableHealthItem{},
				IndexHealth: []platform.IndexHealthItem{},
				QueryStats:  platform.QueryStats{Available: false, Status: "PostgreSQL 监控不可用", Items: []platform.QueryStatsItem{}},
			},
		},
	})

	for _, target := range []string{"/api/system/databases/status", "/api/system/databases/monitoring"} {
		response := httptest.NewRecorder()
		request := authenticatedJSONRequest(http.MethodGet, target, "")
		router.ServeHTTP(response, request)
		assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	}
}

func TestDepartmentEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	department := platform.DepartmentResponse{
		ID:             1,
		DepartmentCode: "OPS",
		DepartmentName: "运营部",
		Status:         "正常",
	}
	provider := fakeDepartmentProvider{
		page:    platform.NewPageResponse([]platform.DepartmentResponse{department}, 1, platform.NormalizePageQuery(0, 20, "", "")),
		options: []platform.DepartmentOptionResponse{{ID: 1, DepartmentCode: "OPS", DepartmentName: "运营部"}},
		detail:  department,
		created: department,
		updated: department,
	}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission: fakePermissionChecker{},
		Department: &provider,
	})

	for _, target := range []string{"/api/departments", "/api/departments/option", "/api/departments/1"} {
		response := httptest.NewRecorder()
		request := authenticatedJSONRequest(http.MethodGet, target, "")
		router.ServeHTTP(response, request)
		assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	}

	for _, tt := range []struct {
		method  string
		target  string
		body    string
		message string
	}{
		{method: http.MethodPost, target: "/api/departments", body: `{"departmentCode":"OPS","departmentName":"运营部","status":"正常"}`, message: "创建成功"},
		{method: http.MethodPut, target: "/api/departments/1", body: `{"departmentCode":"OPS","departmentName":"运营部","status":"正常"}`, message: "更新成功"},
		{method: http.MethodDelete, target: "/api/departments/1", message: "删除成功"},
	} {
		response := httptest.NewRecorder()
		request := authenticatedJSONRequest(tt.method, tt.target, tt.body)
		router.ServeHTTP(response, request)
		assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, tt.message)
	}

	if provider.deletedID != 1 {
		t.Fatalf("deletedID = %d, want 1", provider.deletedID)
	}
}

func TestOperationLogEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	logEntry := platform.OperationLogResponse{
		ID:            1,
		LogNo:         "OP1",
		OperatorName:  "管理员",
		LoginName:     "admin",
		AuthType:      "WEB",
		ModuleName:    "角色设置",
		ActionType:    "查询",
		BusinessNo:    "BIZ-1",
		RequestMethod: "GET",
		RequestPath:   "/api/role-settings",
		ClientIP:      "127.0.0.1",
		ResultStatus:  "成功",
		OperationTime: time.Date(2026, 6, 17, 10, 0, 0, 0, time.UTC),
		Remark:        "ok",
	}
	provider := fakeOperationLogProvider{
		page: platform.NewPageResponse([]platform.OperationLogResponse{logEntry}, 1, platform.NormalizePageQuery(0, 20, "", "")),
	}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission:   fakePermissionChecker{},
		OperationLog: &provider,
	})

	for _, target := range []string{"/api/operation-logs", "/api/operation-logs?keyword=OP1&moduleName=角色设置&actionType=查询&resultStatus=成功&startTime=2026-06-17&endTime=2026-06-17&recordId=1&authType=WEB"} {
		response := httptest.NewRecorder()
		request := authenticatedJSONRequest(http.MethodGet, target, "")
		router.ServeHTTP(response, request)
		assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	}
}

func TestIoReportEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	report := platform.IoReportResponse{
		ID:            1,
		BusinessDate:  "2026-01-10",
		BusinessType:  "采购入库",
		SourceNo:      "PIN-20260110001",
		MaterialCode:  "M-001",
		WarehouseName: "一号库",
		InQuantity:    20,
		QuantityUnit:  "件",
	}
	provider := &fakeIoReportProvider{
		page: platform.NewPageResponse([]platform.IoReportResponse{report}, 1, platform.NormalizePageQuery(1, 20, "businessDate", "asc")),
	}
	permission := &recordingPermissionChecker{
		dataScopeResult: platform.DataScope{
			UserID:       1,
			Resource:     "io-report",
			Action:       "read",
			Scope:        "self",
			OwnerUserIDs: []int64{1},
		},
	}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission: permission,
		IoReport:   provider,
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/io-report?keyword=钢筋&businessType=采购入库&startDate=2026-01-01&endDate=2026-01-31&page=1&size=20&sortBy=businessDate&sortDirection=asc", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var pageBody struct {
		Data platform.PageResponse[platform.IoReportResponse] `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &pageBody); err != nil {
		t.Fatalf("decode page response: %v", err)
	}
	if len(pageBody.Data.Content) != 1 || pageBody.Data.Content[0].SourceNo != "PIN-20260110001" || pageBody.Data.Content[0].ID != 1 {
		t.Fatalf("unexpected io report page: %+v", pageBody.Data)
	}
	if provider.pageQuery.Page != 1 || provider.pageQuery.Size != 20 || provider.pageQuery.SortBy != "businessDate" || provider.pageQuery.Direction != "asc" {
		t.Fatalf("page query = %+v, want page=1 size=20 sortBy=businessDate direction=asc", provider.pageQuery)
	}
	if provider.keyword != "钢筋" || provider.businessType != "采购入库" || provider.startDate != "2026-01-01" || provider.endDate != "2026-01-31" {
		t.Fatalf("page filters = %q/%q/%q/%q, want 钢筋/采购入库/2026-01-01/2026-01-31", provider.keyword, provider.businessType, provider.startDate, provider.endDate)
	}
	if provider.pageDataScope.Scope != "self" || provider.pageDataScope.Resource != "io-report" || provider.pageDataScope.Action != "read" || len(provider.pageDataScope.OwnerUserIDs) != 1 || provider.pageDataScope.OwnerUserIDs[0] != 1 {
		t.Fatalf("page data scope = %+v, want self io-report/read owner 1", provider.pageDataScope)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "io-report", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/io-report?businessType=调拨", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusBadRequest, codeValidationError.Code, "businessType 不合法")
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "io-report", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/io-report?startDate=2026-01-31&endDate=2026-01-01", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusBadRequest, codeValidationError.Code, "startDate 不能晚于 endDate")
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "io-report", action: "read"}})
}

func TestInventoryReportEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	report := platform.InventoryReportResponse{
		ID:             1,
		MaterialCode:   "M-001",
		Brand:          "宝钢",
		Material:       "螺纹钢",
		Category:       "钢材",
		Spec:           "HRB400",
		WarehouseName:  "一号库",
		Quantity:       20,
		QuantityUnit:   "件",
		WeightTon:      12.345,
		Unit:           "吨",
		PieceWeightTon: 0.617,
	}
	provider := &fakeInventoryReportProvider{
		page: platform.NewPageResponse([]platform.InventoryReportResponse{report}, 1, platform.NormalizePageQuery(0, 20, "weightTon", "desc")),
		exportFile: platform.FileDownloadResponse{
			Filename:    "商品库存报表.xlsx",
			ContentType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
			Content:     []byte("xlsx-content"),
		},
	}
	permission := &recordingPermissionChecker{
		dataScopeResult: platform.DataScope{
			UserID:       1,
			Resource:     "inventory-report",
			Action:       "read",
			Scope:        "self",
			OwnerUserIDs: []int64{1},
		},
	}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission:      permission,
		InventoryReport: provider,
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/inventory-report?keyword=钢&warehouseName=一号库&category=钢材&page=0&size=20&sortBy=weightTon&sortDirection=desc", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var pageBody struct {
		Data platform.PageResponse[platform.InventoryReportResponse] `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &pageBody); err != nil {
		t.Fatalf("decode page response: %v", err)
	}
	if len(pageBody.Data.Content) != 1 || pageBody.Data.Content[0].MaterialCode != "M-001" || pageBody.Data.Content[0].ID != 1 {
		t.Fatalf("unexpected inventory report page: %+v", pageBody.Data)
	}
	if provider.pageQuery.Page != 0 || provider.pageQuery.Size != 20 || provider.pageQuery.SortBy != "weightTon" || provider.pageQuery.Direction != "desc" {
		t.Fatalf("page query = %+v, want page=0 size=20 sortBy=weightTon direction=desc", provider.pageQuery)
	}
	if provider.keyword != "钢" || provider.warehouseName != "一号库" || provider.category != "钢材" {
		t.Fatalf("page filters = %q/%q/%q, want 钢/一号库/钢材", provider.keyword, provider.warehouseName, provider.category)
	}
	if provider.pageDataScope.Scope != "self" || provider.pageDataScope.Resource != "inventory-report" || provider.pageDataScope.Action != "read" || len(provider.pageDataScope.OwnerUserIDs) != 1 || provider.pageDataScope.OwnerUserIDs[0] != 1 {
		t.Fatalf("page data scope = %+v, want self inventory-report/read owner 1", provider.pageDataScope)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "inventory-report", action: "read"}})

	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/inventory-report?sortBy=materialCode", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusBadRequest, codeValidationError.Code, "sortBy 不支持当前列表")

	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/inventory-report?sortDirection=up", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusBadRequest, codeValidationError.Code, "sortDirection 只能为 asc 或 desc")

	permission.calls = nil
	permission.dataScopeResult = platform.DataScope{
		UserID:       1,
		Resource:     "inventory-report",
		Action:       "export",
		Scope:        "self",
		OwnerUserIDs: []int64{1},
	}
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPost, "/api/inventory-report/export", `{"keyword":"钢","warehouseName":"一号库","category":"钢材"}`)
	router.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("export status = %d, want %d; body = %s", response.Code, http.StatusOK, response.Body.String())
	}
	if got := response.Header().Get("Content-Type"); got != "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" {
		t.Fatalf("export content type = %q", got)
	}
	if got := response.Header().Get("Content-Disposition"); !strings.Contains(got, "attachment") || !strings.Contains(got, "filename*=") {
		t.Fatalf("export content disposition = %q, want attachment with encoded filename", got)
	}
	if got := response.Header().Get("Content-Length"); got != "12" {
		t.Fatalf("export content length = %q, want 12", got)
	}
	if response.Body.String() != "xlsx-content" {
		t.Fatalf("export body = %q", response.Body.String())
	}
	if provider.exportKeyword != "钢" || provider.exportWarehouseName != "一号库" || provider.exportCategory != "钢材" {
		t.Fatalf("export filters = %q/%q/%q, want 钢/一号库/钢材", provider.exportKeyword, provider.exportWarehouseName, provider.exportCategory)
	}
	if provider.exportDataScope.Scope != "self" || provider.exportDataScope.Resource != "inventory-report" || provider.exportDataScope.Action != "export" || len(provider.exportDataScope.OwnerUserIDs) != 1 || provider.exportDataScope.OwnerUserIDs[0] != 1 {
		t.Fatalf("export data scope = %+v, want self inventory-report/export owner 1", provider.exportDataScope)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "inventory-report", action: "export"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPost, "/api/inventory-report/export", "")
	router.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("empty export status = %d, want %d; body = %s", response.Code, http.StatusOK, response.Body.String())
	}
	if provider.exportKeyword != "" || provider.exportWarehouseName != "" || provider.exportCategory != "" {
		t.Fatalf("empty export filters = %q/%q/%q, want empty", provider.exportKeyword, provider.exportWarehouseName, provider.exportCategory)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "inventory-report", action: "export"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPost, "/api/inventory-report/export", "")
	request.ContentLength = -1
	router.ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("unknown length empty export status = %d, want %d; body = %s", response.Code, http.StatusOK, response.Body.String())
	}
	if provider.exportKeyword != "" || provider.exportWarehouseName != "" || provider.exportCategory != "" {
		t.Fatalf("unknown length empty export filters = %q/%q/%q, want empty", provider.exportKeyword, provider.exportWarehouseName, provider.exportCategory)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "inventory-report", action: "export"}})
}

func TestPendingInvoiceReceiptReportEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	report := platform.PendingInvoiceReceiptReportResponse{
		ID:                      1,
		OrderNo:                 "PO-20260110001",
		SupplierName:            "华东供应商",
		InvoiceTitle:            "华东供应商",
		OrderDate:               "2026-01-10T00:00:00",
		MaterialCode:            "M-001",
		Brand:                   "宝钢",
		Material:                "螺纹钢",
		Category:                "钢材",
		Spec:                    "HRB400",
		OrderQuantity:           20,
		QuantityUnit:            "件",
		OrderWeightTon:          12.345,
		PendingInvoiceWeightTon: 12.345,
		UnitPrice:               3500,
		OrderAmount:             43207.5,
		PendingInvoiceAmount:    43207.5,
		Status:                  "未收票",
	}
	provider := &fakePendingInvoiceReceiptReportProvider{
		page: platform.NewPageResponse([]platform.PendingInvoiceReceiptReportResponse{report}, 1, platform.NormalizePageQuery(0, 20, "pendingInvoiceAmount", "desc")),
	}
	permission := &recordingPermissionChecker{
		dataScopeResult: platform.DataScope{
			UserID:       1,
			Resource:     "pending-invoice-receipt-report",
			Action:       "read",
			Scope:        "self",
			OwnerUserIDs: []int64{1},
		},
	}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission:                  permission,
		PendingInvoiceReceiptReport: provider,
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/pending-invoice-receipt-report?keyword=PO&supplierName=华东供应商&startDate=2026-01-01&endDate=2026-01-31&page=0&size=20&sortBy=pendingInvoiceAmount&sortDirection=desc", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var pageBody struct {
		Data platform.PageResponse[platform.PendingInvoiceReceiptReportResponse] `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &pageBody); err != nil {
		t.Fatalf("decode page response: %v", err)
	}
	if len(pageBody.Data.Content) != 1 || pageBody.Data.Content[0].OrderNo != "PO-20260110001" || pageBody.Data.Content[0].ID != 1 {
		t.Fatalf("unexpected pending invoice receipt report page: %+v", pageBody.Data)
	}
	if provider.pageQuery.Page != 0 || provider.pageQuery.Size != 20 || provider.pageQuery.SortBy != "pendingInvoiceAmount" || provider.pageQuery.Direction != "desc" {
		t.Fatalf("page query = %+v, want page=0 size=20 sortBy=pendingInvoiceAmount direction=desc", provider.pageQuery)
	}
	if provider.keyword != "PO" || provider.supplierName != "华东供应商" || provider.startDate != "2026-01-01" || provider.endDate != "2026-01-31" {
		t.Fatalf("page filters = %q/%q/%q/%q, want PO/华东供应商/2026-01-01/2026-01-31", provider.keyword, provider.supplierName, provider.startDate, provider.endDate)
	}
	if provider.pageDataScope.Scope != "self" || provider.pageDataScope.Resource != "pending-invoice-receipt-report" || provider.pageDataScope.Action != "read" || len(provider.pageDataScope.OwnerUserIDs) != 1 || provider.pageDataScope.OwnerUserIDs[0] != 1 {
		t.Fatalf("page data scope = %+v, want self pending-invoice-receipt-report/read owner 1", provider.pageDataScope)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "pending-invoice-receipt-report", action: "read"}})

	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/pending-invoice-receipt-report?sortBy=id", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusBadRequest, codeValidationError.Code, "sortBy 不支持当前列表")

	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/pending-invoice-receipt-report?sortDirection=up", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusBadRequest, codeValidationError.Code, "sortDirection 只能为 asc 或 desc")
}

func TestGeneralSettingsEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	setting := platform.GeneralSettingResponse{
		ID:           1,
		SettingCode:  "RULE_SO",
		SettingName:  "销售订单编号规则",
		BillName:     "销售订单",
		Prefix:       "SO{yyyy}{seq}",
		DateRule:     "yyyy",
		SerialLength: 6,
		ResetRule:    "YEARLY",
		SampleNo:     "SO2026000001",
		Status:       "正常",
		RuleType:     "NO_RULE",
	}
	noRule := platform.NoRuleResponse{
		ID:           1,
		SettingCode:  "RULE_SO",
		SettingName:  "销售订单编号规则",
		BillName:     "销售订单",
		Prefix:       "SO{yyyy}{seq}",
		DateRule:     "yyyy",
		SerialLength: 6,
		ResetRule:    "YEARLY",
		SampleNo:     "SO2026000001",
		Status:       "正常",
	}
	provider := fakeGeneralSettingProvider{
		page:      platform.NewPageResponse([]platform.GeneralSettingResponse{setting}, 1, platform.NormalizePageQuery(0, 20, "", "")),
		display:   []platform.GeneralSettingResponse{setting},
		client:    []platform.GeneralSettingResponse{setting},
		statement: platform.StatementGeneratorRulesResponse{CustomerStatementReceiptAmountZero: true, SupplierStatementFullPayment: true},
		detail:    noRule,
		next:      platform.NoRuleGenerateResponse{ModuleKey: "sales-order", GeneratedNo: "SO2026000001"},
		created:   noRule,
		updated:   noRule,
	}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission:     fakePermissionChecker{},
		GeneralSetting: &provider,
	})

	for _, target := range []string{
		"/api/general-settings",
		"/api/general-settings/display-switch",
		"/api/general-settings/client-setting",
		"/api/general-settings/statement-generator-rule",
		"/api/general-settings/1",
	} {
		response := httptest.NewRecorder()
		request := authenticatedJSONRequest(http.MethodGet, target, "")
		router.ServeHTTP(response, request)
		assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	}

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodPost, "/api/general-settings/number-rule/next?moduleKey=sales-order", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)

	for _, tt := range []struct {
		method  string
		target  string
		body    string
		message string
	}{
		{method: http.MethodPost, target: "/api/general-settings", body: `{"settingCode":"RULE_SO","settingName":"销售订单编号规则","billName":"销售订单","prefix":"SO{yyyy}{seq}","dateRule":"yyyy","serialLength":6,"resetRule":"YEARLY","sampleNo":"SO2026000001","status":"正常"}`, message: "创建成功"},
		{method: http.MethodPut, target: "/api/general-settings/1", body: `{"settingCode":"RULE_SO","settingName":"销售订单编号规则","billName":"销售订单","prefix":"SO{yyyy}{seq}","dateRule":"yyyy","serialLength":6,"resetRule":"YEARLY","sampleNo":"SO2026000001","status":"正常"}`, message: "更新成功"},
		{method: http.MethodDelete, target: "/api/general-settings/1", message: "删除成功"},
	} {
		response := httptest.NewRecorder()
		request := authenticatedJSONRequest(tt.method, tt.target, tt.body)
		router.ServeHTTP(response, request)
		assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, tt.message)
	}

	if provider.deletedID != 1 {
		t.Fatalf("deletedID = %d, want 1", provider.deletedID)
	}
}

func TestUploadRuleEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	rule := platform.UploadRuleResponse{
		ID:              1,
		ModuleKey:       "sales-order",
		ModuleName:      "销售订单",
		RuleCode:        "PAGE_UPLOAD_SALES_ORDER",
		RuleName:        "销售订单上传命名规则",
		RenamePattern:   "{年月日时分秒}_{random8}",
		Status:          "正常",
		PreviewFileName: "20260424123045_preview1.pdf",
	}
	provider := fakeUploadRuleProvider{detail: rule, updated: rule}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission: fakePermissionChecker{},
		UploadRule: &provider,
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/general-settings/upload-rule?moduleKey=sales-order", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)

	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPut, "/api/general-settings/upload-rule?moduleKey=sales-order", `{"renamePattern":"{年月日时分秒}_{random8}","status":"正常"}`)
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "更新成功")
}

func TestSecurityKeyEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	now := time.Date(2026, 6, 17, 10, 0, 0, 0, time.UTC)
	item := platform.SecurityKeyItemResponse{
		KeyCode:           "JWT_MASTER",
		KeyName:           "JWT 主密钥",
		Source:            "DATABASE",
		ActiveVersion:     1,
		ActiveFingerprint: "ABCDEF123456",
		ActivatedAt:       &now,
		Remark:            "当前使用数据库托管主密钥，轮转后立即对新会话生效",
	}
	rotated := platform.SecurityKeyRotateResponse{
		KeyCode:           "JWT_MASTER",
		Source:            "DATABASE",
		ActiveVersion:     2,
		ActiveFingerprint: "123456ABCDEF",
		RotatedAt:         now,
		Remark:            "JWT 主密钥轮转完成",
	}
	provider := fakeSecurityKeyProvider{
		overview: platform.SecurityKeyOverviewResponse{JWT: item, TOTP: item},
		jwt:      rotated,
		totp:     rotated,
	}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin", TotpEnabled: true},
	}, RouterServices{
		Permission:  fakePermissionChecker{},
		SecurityKey: provider,
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/system/security-keys", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)

	for _, tt := range []struct {
		target  string
		message string
	}{
		{target: "/api/system/security-keys/jwt/rotate", message: "JWT 主密钥轮转成功"},
		{target: "/api/system/security-keys/totp/rotate", message: "2FA 主密钥轮转成功"},
	} {
		response := httptest.NewRecorder()
		request := authenticatedJSONRequest(http.MethodPost, tt.target, "")
		request.Header.Set("X-TOTP-Code", "123456")
		router.ServeHTTP(response, request)
		assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, tt.message)
	}
}

func TestRoleSettingsEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	role := platform.RoleSettingResponse{ID: 1, RoleCode: "ADMIN", RoleName: "管理员", Status: "正常"}
	permissions := []platform.RolePermissionItem{{Resource: "role", Action: "read"}}
	options := []platform.MenuNode{{MenuCode: "permission-group-1", MenuName: "系统", MenuType: "目录"}}
	templates := []platform.RoleTemplate{{Name: "管理员", Description: "全部资源全部操作"}}
	provider := fakeRoleSettingProvider{
		page:        platform.NewPageResponse([]platform.RoleSettingResponse{role}, 1, platform.NormalizePageQuery(0, 20, "", "")),
		detail:      role,
		created:     role,
		updated:     role,
		permissions: permissions,
		options:     options,
		templates:   templates,
	}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission:  fakePermissionChecker{},
		RoleSetting: &provider,
	})

	for _, target := range []string{"/api/role-settings", "/api/role-settings/1", "/api/role-settings/1/permission", "/api/role-settings/permission-option", "/api/role-settings/templates"} {
		response := httptest.NewRecorder()
		request := authenticatedJSONRequest(http.MethodGet, target, "")
		router.ServeHTTP(response, request)
		assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	}

	for _, tt := range []struct {
		method  string
		target  string
		body    string
		message string
	}{
		{method: http.MethodPost, target: "/api/role-settings", body: `{"roleCode":"OPS","roleName":"运营","roleType":"业务角色","dataScope":"本部门","status":"正常"}`, message: "创建成功"},
		{method: http.MethodPut, target: "/api/role-settings/1", body: `{"roleCode":"OPS","roleName":"运营","roleType":"业务角色","dataScope":"本部门","status":"正常"}`, message: "更新成功"},
		{method: http.MethodDelete, target: "/api/role-settings/1", message: "删除成功"},
		{method: http.MethodPut, target: "/api/role-settings/1/permission", body: `[{"resource":"role","action":"read"}]`, message: "权限保存成功"},
	} {
		response := httptest.NewRecorder()
		request := authenticatedJSONRequest(tt.method, tt.target, tt.body)
		router.ServeHTTP(response, request)
		assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, tt.message)
	}
}

func TestGlobalSearchEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		GlobalSearch: fakeGlobalSearcher{results: []platform.GlobalSearchResult{{
			ModuleKey: "sales-order",
			Title:     "销售订单",
			TrackID:   "1",
			PrimaryNo: "SO-001",
			Summary:   "客户 / 正常",
		}}},
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/global-search?keyword=SO&limit=20&moduleKeys=sales-order", "")
	router.ServeHTTP(response, request)

	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var body struct {
		Data []struct {
			ModuleKey string `json:"moduleKey"`
			PrimaryNo string `json:"primaryNo"`
		} `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if len(body.Data) != 1 || body.Data[0].ModuleKey != "sales-order" || body.Data[0].PrimaryNo != "SO-001" {
		t.Fatalf("unexpected global search response: %+v", body.Data)
	}
}

func TestGlobalSearchEndpointRequiresAuthentication(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := newServicesTestRouter(fakeAuthProvider{
		authenticateErr: platform.NewAuthError(platform.AuthErrorUnauthorized, "未登录"),
	}, RouterServices{GlobalSearch: fakeGlobalSearcher{}})

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodGet, "/api/global-search?keyword=SO", nil)
	router.ServeHTTP(response, request)

	assertAPIResponse(t, response, http.StatusUnauthorized, codeUnauthorized.Code, "未登录")
}

func TestPrintTemplateEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	template := platform.PrintTemplateResponse{
		ID:           "1",
		TemplateName: "默认模板",
		TemplateCode: "TPL_1",
		TemplateHtml: "{\"static\":[]}",
		BillType:     "sales-order",
		TemplateType: "PDF_FORM",
		Engine:       "PDF_FORM",
		VersionNo:    1,
		Status:       "ACTIVE",
		SyncMode:     "MANUAL",
	}
	provider := fakePrintTemplateProvider{
		list:     []platform.PrintTemplateResponse{template},
		billType: "sales-order",
		created:  template,
		updated:  template,
	}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission:    fakePermissionChecker{},
		PrintTemplate: &provider,
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/print-templates?billType=sales-order", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var body struct {
		Data []platform.PrintTemplateResponse `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if len(body.Data) != 1 || body.Data[0].ID != "1" || body.Data[0].BillType != "sales-order" {
		t.Fatalf("unexpected print template list: %+v", body.Data)
	}
	if provider.listBillType != "sales-order" {
		t.Fatalf("list billType = %q, want sales-order", provider.listBillType)
	}

	for _, tt := range []struct {
		method  string
		target  string
		body    string
		message string
	}{
		{method: http.MethodPost, target: "/api/print-templates", body: `{"billType":"sales-order","templateName":"默认模板","templateCode":"tpl_1","templateHtml":"{\"static\":[]}","templateType":"PDF_FORM","engine":"PDF_FORM","versionNo":1,"status":"ACTIVE"}`, message: "创建成功"},
		{method: http.MethodPut, target: "/api/print-templates/1", body: `{"billType":"sales-order","templateName":"默认模板","templateCode":"tpl_1","templateHtml":"{\"static\":[]}","templateType":"PDF_FORM","engine":"PDF_FORM","versionNo":1,"status":"ACTIVE"}`, message: "更新成功"},
		{method: http.MethodDelete, target: "/api/print-templates/1", message: "删除成功"},
	} {
		response := httptest.NewRecorder()
		request := authenticatedJSONRequest(tt.method, tt.target, tt.body)
		router.ServeHTTP(response, request)
		assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, tt.message)
	}
	if provider.updateID != 1 || provider.deleteID != 1 {
		t.Fatalf("updateID/deleteID = %d/%d, want 1/1", provider.updateID, provider.deleteID)
	}
}

func TestPrintTemplateUploadJSONEndpoint(t *testing.T) {
	gin.SetMode(gin.TestMode)
	uploaded := platform.PrintTemplateResponse{
		ID:           "1",
		TemplateName: "默认模板",
		TemplateCode: "TPL_1",
		TemplateHtml: "{\"static\":[]}",
		BillType:     "sales-order",
		TemplateType: "PDF_FORM",
		Engine:       "PDF_FORM",
		VersionNo:    2,
		Status:       "ACTIVE",
		SyncMode:     "MANUAL",
	}
	provider := fakePrintTemplateProvider{billType: "sales-order", uploaded: uploaded}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission:    fakePermissionChecker{},
		PrintTemplate: &provider,
	})

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, err := writer.CreateFormFile("file", "template.json")
	if err != nil {
		t.Fatalf("create multipart form file: %v", err)
	}
	if _, err := part.Write([]byte("{\"static\":[]}")); err != nil {
		t.Fatalf("write multipart file: %v", err)
	}
	if err := writer.Close(); err != nil {
		t.Fatalf("close multipart writer: %v", err)
	}

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodPost, "/api/print-templates/1/upload-json", &buf)
	request.Header.Set("Authorization", "Bearer access-token")
	request.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "上传成功")

	if provider.uploadID != 1 || provider.uploadFilename != "template.json" || string(provider.uploadContent) != "{\"static\":[]}" {
		t.Fatalf("unexpected upload args: id=%d filename=%q content=%q", provider.uploadID, provider.uploadFilename, string(provider.uploadContent))
	}
}

func TestPrintTemplateModulePermissionFallbackAndCrossBillTypeUpdate(t *testing.T) {
	gin.SetMode(gin.TestMode)
	template := platform.PrintTemplateResponse{
		ID:           "1",
		TemplateName: "默认模板",
		TemplateCode: "TPL_1",
		TemplateHtml: "{\"static\":[]}",
		BillType:     "sales-order",
		TemplateType: "PDF_FORM",
		Engine:       "PDF_FORM",
		VersionNo:    1,
		Status:       "ACTIVE",
		SyncMode:     "MANUAL",
	}
	permission := &recordingPermissionChecker{
		failures: map[string]error{
			"sales-order:print": platform.NewAuthError(platform.AuthErrorForbidden, "无权访问"),
		},
	}
	provider := fakePrintTemplateProvider{
		list:     []platform.PrintTemplateResponse{template},
		billType: "sales-order",
		updated:  template,
	}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission:    permission,
		PrintTemplate: &provider,
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/print-templates?billType=sales-order", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	assertPermissionCalls(t, permission.calls, []permissionCall{
		{resource: "sales-order", action: "print"},
		{resource: "sales-order", action: "read"},
	})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPut, "/api/print-templates/1", `{"billType":"purchase-order","templateName":"默认模板","templateCode":"tpl_1","templateHtml":"{\"static\":[]}","templateType":"PDF_FORM","engine":"PDF_FORM","versionNo":1,"status":"ACTIVE"}`)
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "更新成功")
	assertPermissionCalls(t, permission.calls, []permissionCall{
		{resource: "print-template", action: "update"},
		{resource: "sales-order", action: "update"},
		{resource: "purchase-order", action: "update"},
	})
}

func TestMaterialCategoryEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	category := platform.MaterialCategoryResponse{
		ID:                    "1",
		CategoryCode:          "REBAR",
		CategoryName:          "螺纹钢",
		SortOrder:             1,
		PurchaseWeighRequired: false,
		Status:                "正常",
		Remark:                "备注",
	}
	provider := &fakeMaterialCategoryProvider{
		page:    platform.NewPageResponse([]platform.MaterialCategoryResponse{category}, 1, platform.NormalizePageQuery(0, 20, "", "")),
		detail:  category,
		created: category,
		updated: category,
		options: []platform.MaterialCategoryOptionResponse{{
			Value:                 "螺纹钢",
			Label:                 "螺纹钢",
			PurchaseWeighRequired: false,
		}},
	}
	permission := &recordingPermissionChecker{
		dataScopeResult: platform.DataScope{
			UserID:       1,
			Resource:     "customer",
			Action:       "read",
			Scope:        "self",
			OwnerUserIDs: []int64{1},
		},
	}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission:       permission,
		MaterialCategory: provider,
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/material-categories?keyword=螺&status=正常", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var pageBody struct {
		Data platform.PageResponse[platform.MaterialCategoryResponse] `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &pageBody); err != nil {
		t.Fatalf("decode page response: %v", err)
	}
	if len(pageBody.Data.Content) != 1 || pageBody.Data.Content[0].CategoryCode != "REBAR" || pageBody.Data.Content[0].ID != "1" {
		t.Fatalf("unexpected material category page: %+v", pageBody.Data)
	}
	if provider.pageKeyword != "螺" || provider.pageStatus != "正常" {
		t.Fatalf("page filters = %q/%q, want 螺/正常", provider.pageKeyword, provider.pageStatus)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "material", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/material-categories/option", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var optionBody struct {
		Data []platform.MaterialCategoryOptionResponse `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &optionBody); err != nil {
		t.Fatalf("decode option response: %v", err)
	}
	if len(optionBody.Data) != 1 || optionBody.Data[0].Value != "螺纹钢" || optionBody.Data[0].Label != "螺纹钢" {
		t.Fatalf("unexpected material category options: %+v", optionBody.Data)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "material", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/material-categories/1", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	if provider.detailID != 1 {
		t.Fatalf("detailID = %d, want 1", provider.detailID)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "material", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPost, "/api/material-categories", `{"categoryCode":"REBAR","categoryName":"螺纹钢","sortOrder":1,"purchaseWeighRequired":false,"status":"正常","remark":"备注"}`)
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "创建成功")
	if provider.createRequest.CategoryCode != "REBAR" || provider.createRequest.CategoryName != "螺纹钢" {
		t.Fatalf("unexpected create request: %+v", provider.createRequest)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "material", action: "create"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPut, "/api/material-categories/1", `{"categoryCode":"REBAR","categoryName":"螺纹钢","sortOrder":2,"purchaseWeighRequired":true,"status":"正常"}`)
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "更新成功")
	if provider.updateID != 1 || provider.updateRequest.SortOrder == nil || *provider.updateRequest.SortOrder != 2 {
		t.Fatalf("unexpected update args: id=%d request=%+v", provider.updateID, provider.updateRequest)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "material", action: "update"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodDelete, "/api/material-categories/1", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "删除成功")
	if provider.deleteID != 1 {
		t.Fatalf("deleteID = %d, want 1", provider.deleteID)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "material", action: "delete"}})

	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/material-categories?page=-1", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusBadRequest, codeValidationError.Code, "page 不能小于0")

	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/material-categories?size=0", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusBadRequest, codeValidationError.Code, "size 必须在1到200之间")

	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/material-categories?direction=up", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusBadRequest, codeValidationError.Code, "direction 只能为 asc 或 desc")

	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/material-categories?sortBy=bad-field", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusBadRequest, codeValidationError.Code, "sortBy 格式不合法")

}

func TestCustomerEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	customer := platform.CustomerResponse{
		ID:              1,
		CustomerCode:    "CUST001",
		CustomerName:    "华东客户",
		ContactName:     strPtr("张三"),
		ContactPhone:    strPtr("13800138000"),
		City:            strPtr("上海"),
		SettlementMode:  strPtr("月结"),
		ProjectName:     "华东客户项目",
		ProjectNameAbbr: strPtr("华东"),
		ProjectAddress:  strPtr("上海市浦东新区"),
		Status:          "正常",
		Remark:          strPtr("备注"),
	}
	provider := &fakeCustomerProvider{
		page:    platform.NewPageResponse([]platform.CustomerResponse{customer}, 1, platform.NormalizePageQuery(0, 20, "", "")),
		detail:  customer,
		created: customer,
		updated: customer,
		options: []platform.CustomerOptionResponse{{
			ID:              1,
			Label:           "华东客户 / 华东客户项目",
			Value:           "华东客户",
			CustomerCode:    "CUST001",
			CustomerName:    "华东客户",
			ProjectName:     "华东客户项目",
			ProjectNameAbbr: strPtr("华东"),
		}},
	}
	permission := &recordingPermissionChecker{
		dataScopeResult: platform.DataScope{
			UserID:       1,
			Resource:     "customer",
			Action:       "read",
			Scope:        "self",
			OwnerUserIDs: []int64{1},
		},
	}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission: permission,
		Customer:   provider,
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/customers?keyword=客户&status=正常", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var pageBody struct {
		Data platform.PageResponse[platform.CustomerResponse] `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &pageBody); err != nil {
		t.Fatalf("decode page response: %v", err)
	}
	if len(pageBody.Data.Content) != 1 || pageBody.Data.Content[0].CustomerCode != "CUST001" || pageBody.Data.Content[0].ID != 1 {
		t.Fatalf("unexpected customer page: %+v", pageBody.Data)
	}
	if provider.pageKeyword != "客户" || provider.pageStatus != "正常" {
		t.Fatalf("page filters = %q/%q, want 客户/正常", provider.pageKeyword, provider.pageStatus)
	}
	if provider.pageDataScope.Scope != "self" || len(provider.pageDataScope.OwnerUserIDs) != 1 || provider.pageDataScope.OwnerUserIDs[0] != 1 {
		t.Fatalf("page data scope = %+v, want self owner 1", provider.pageDataScope)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "customer", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/customers/option", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var optionBody struct {
		Data []platform.CustomerOptionResponse `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &optionBody); err != nil {
		t.Fatalf("decode option response: %v", err)
	}
	if len(optionBody.Data) != 1 || optionBody.Data[0].Value != "华东客户" || optionBody.Data[0].Label != "华东客户 / 华东客户项目" {
		t.Fatalf("unexpected customer options: %+v", optionBody.Data)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "customer", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/customers/1", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	if provider.detailID != 1 {
		t.Fatalf("detailID = %d, want 1", provider.detailID)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "customer", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPost, "/api/customers", `{"customerCode":"CUST001","customerName":"华东客户","contactName":"张三","contactPhone":"13800138000","city":"上海","settlementMode":"月结","projectName":"华东客户项目","projectNameAbbr":"华东","projectAddress":"上海市浦东新区","status":"正常","remark":"备注"}`)
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "创建成功")
	if provider.createRequest.CustomerCode != "CUST001" || provider.createRequest.ProjectName != "华东客户项目" {
		t.Fatalf("unexpected create request: %+v", provider.createRequest)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "customer", action: "create"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPut, "/api/customers/1", `{"customerCode":"CUST001","customerName":"华东客户","contactName":"张三","contactPhone":"13800138000","city":"上海","settlementMode":"月结","projectName":"华东客户项目","projectNameAbbr":"华东","projectAddress":"上海市浦东新区","status":"正常","remark":"备注"}`)
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "更新成功")
	if provider.updateID != 1 || provider.updateRequest.CustomerName != "华东客户" {
		t.Fatalf("unexpected update args: id=%d request=%+v", provider.updateID, provider.updateRequest)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "customer", action: "update"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodDelete, "/api/customers/1", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "删除成功")
	if provider.deleteID != 1 {
		t.Fatalf("deleteID = %d, want 1", provider.deleteID)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "customer", action: "delete"}})

	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/customers?sortBy=bad-field", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusBadRequest, codeValidationError.Code, "sortBy 格式不合法")

	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/customers?sortBy=projectNameAbbr", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusBadRequest, codeValidationError.Code, "sortBy 不支持当前列表")
}

func TestProjectEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	project := platform.ProjectResponse{
		ID:              1,
		ProjectCode:     "PROJ001",
		ProjectName:     "华东项目",
		ProjectNameAbbr: strPtr("华东"),
		ProjectAddress:  strPtr("上海市浦东新区"),
		ProjectManager:  strPtr("赵经理"),
		CustomerCode:    "CUST001",
		Status:          "正常",
		Remark:          strPtr("备注"),
	}
	provider := &fakeProjectProvider{
		page:    platform.NewPageResponse([]platform.ProjectResponse{project}, 1, platform.NormalizePageQuery(0, 20, "projectNameAbbr", "asc")),
		detail:  project,
		created: project,
		updated: project,
	}
	permission := &recordingPermissionChecker{
		dataScopeResult: platform.DataScope{
			UserID:       1,
			Resource:     "project",
			Action:       "read",
			Scope:        "self",
			OwnerUserIDs: []int64{1},
		},
	}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission: permission,
		Project:    provider,
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/projects?keyword=项目&status=正常&page=0&size=20&sortBy=projectNameAbbr&direction=asc", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var pageBody struct {
		Data platform.PageResponse[platform.ProjectResponse] `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &pageBody); err != nil {
		t.Fatalf("decode page response: %v", err)
	}
	if len(pageBody.Data.Content) != 1 || pageBody.Data.Content[0].ProjectCode != "PROJ001" || pageBody.Data.Content[0].ID != 1 {
		t.Fatalf("unexpected project page: %+v", pageBody.Data)
	}
	if provider.pageKeyword != "项目" || provider.pageStatus != "正常" {
		t.Fatalf("page filters = %q/%q, want 项目/正常", provider.pageKeyword, provider.pageStatus)
	}
	if provider.pageQuery.Page != 0 || provider.pageQuery.Size != 20 || provider.pageQuery.SortBy != "projectNameAbbr" || provider.pageQuery.Direction != "asc" {
		t.Fatalf("page query = %+v, want page=0 size=20 sortBy=projectNameAbbr direction=asc", provider.pageQuery)
	}
	if provider.pageDataScope.Scope != "self" || len(provider.pageDataScope.OwnerUserIDs) != 1 || provider.pageDataScope.OwnerUserIDs[0] != 1 {
		t.Fatalf("page data scope = %+v, want self owner 1", provider.pageDataScope)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "project", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/projects/1", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	if provider.detailID != 1 {
		t.Fatalf("detailID = %d, want 1", provider.detailID)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "project", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPost, "/api/projects", `{"projectCode":"PROJ002","projectName":"华南项目","projectNameAbbr":"华南","projectAddress":"广州市天河区","projectManager":"钱经理","customerCode":"CUST002","status":"正常","remark":"备注"}`)
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "创建成功")
	if provider.createRequest.ProjectCode != "PROJ002" || provider.createRequest.CustomerCode != "CUST002" {
		t.Fatalf("unexpected create request: %+v", provider.createRequest)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "project", action: "create"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPut, "/api/projects/1", `{"projectCode":"PROJ001","projectName":"华东项目","projectNameAbbr":"华东","projectAddress":"上海市浦东新区","projectManager":"赵经理","customerCode":"CUST001","status":"正常","remark":"备注"}`)
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "更新成功")
	if provider.updateID != 1 || provider.updateRequest.ProjectName != "华东项目" {
		t.Fatalf("unexpected update args: id=%d request=%+v", provider.updateID, provider.updateRequest)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "project", action: "update"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodDelete, "/api/projects/1", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "删除成功")
	if provider.deleteID != 1 {
		t.Fatalf("deleteID = %d, want 1", provider.deleteID)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "project", action: "delete"}})

	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/projects?sortBy=bad-field", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusBadRequest, codeValidationError.Code, "sortBy 格式不合法")

	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/projects?sortBy=projectAddress", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusBadRequest, codeValidationError.Code, "sortBy 不支持当前列表")
}

func TestCarrierEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	carrier := platform.CarrierResponse{
		ID:           1,
		CarrierCode:  "CAR001",
		CarrierName:  "华东物流",
		ContactName:  strPtr("李四"),
		ContactPhone: strPtr("13900139000"),
		VehicleType:  strPtr("平板车"),
		Vehicles: []platform.VehicleInfo{{
			ID:      11,
			Plate:   "沪A12345",
			Contact: strPtr("司机甲"),
			Phone:   strPtr("138****8000"),
			Remark:  strPtr("主车"),
		}},
		PriceMode: strPtr("按吨"),
		Status:    "正常",
		Remark:    strPtr("备注"),
	}
	provider := &fakeCarrierProvider{
		page:    platform.NewPageResponse([]platform.CarrierResponse{carrier}, 1, platform.NormalizePageQuery(0, 20, "", "")),
		detail:  carrier,
		created: carrier,
		updated: carrier,
		options: []platform.CarrierOptionResponse{{
			ID:            1,
			Label:         "华东物流",
			Value:         "华东物流",
			VehiclePlates: []string{"沪A12345"},
		}},
	}
	permission := &recordingPermissionChecker{}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission: permission,
		Carrier:    provider,
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/carriers?keyword=物流&status=正常", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var pageBody struct {
		Data platform.PageResponse[platform.CarrierResponse] `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &pageBody); err != nil {
		t.Fatalf("decode page response: %v", err)
	}
	if len(pageBody.Data.Content) != 1 || pageBody.Data.Content[0].CarrierCode != "CAR001" || pageBody.Data.Content[0].ID != 1 {
		t.Fatalf("unexpected carrier page: %+v", pageBody.Data)
	}
	if provider.pageKeyword != "物流" || provider.pageStatus != "正常" {
		t.Fatalf("page filters = %q/%q, want 物流/正常", provider.pageKeyword, provider.pageStatus)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "carrier", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/carriers/option", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var optionBody struct {
		Data []platform.CarrierOptionResponse `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &optionBody); err != nil {
		t.Fatalf("decode option response: %v", err)
	}
	if len(optionBody.Data) != 1 || len(optionBody.Data[0].VehiclePlates) != 1 || optionBody.Data[0].VehiclePlates[0] != "沪A12345" {
		t.Fatalf("unexpected carrier options: %+v", optionBody.Data)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "carrier", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/carriers/1", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	if provider.detailID != 1 {
		t.Fatalf("detailID = %d, want 1", provider.detailID)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "carrier", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPost, "/api/carriers", `{"carrierCode":"CAR002","carrierName":"华南物流","vehicleType":"厢车","vehicles":[{"plate":"粤B12345","contact":"司机乙","phone":"13800138000","remark":"备用"}],"priceMode":"按吨","status":"正常"}`)
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "创建成功")
	if provider.createRequest.CarrierCode != "CAR002" || len(provider.createRequest.Vehicles) != 1 || provider.createRequest.Vehicles[0].Plate != "粤B12345" {
		t.Fatalf("unexpected create request: %+v", provider.createRequest)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "carrier", action: "create"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPut, "/api/carriers/1", `{"carrierCode":"CAR001","carrierName":"华东物流","status":"正常"}`)
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "更新成功")
	if provider.updateID != 1 || provider.updateRequest.CarrierCode != "CAR001" {
		t.Fatalf("unexpected update args: id=%d request=%+v", provider.updateID, provider.updateRequest)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "carrier", action: "update"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodDelete, "/api/carriers/1", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "删除成功")
	if provider.deleteID != 1 {
		t.Fatalf("deleteID = %d, want 1", provider.deleteID)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "carrier", action: "delete"}})
}

func TestWarehouseEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	warehouse := platform.WarehouseResponse{
		ID:            1,
		WarehouseCode: "WH001",
		WarehouseName: "一号库",
		WarehouseType: "自有",
		ContactName:   strPtr("王五"),
		ContactPhone:  strPtr("13700137000"),
		Address:       strPtr("上海市宝山区"),
		Status:        "正常",
		Remark:        strPtr("备注"),
	}
	provider := &fakeWarehouseProvider{
		page:    platform.NewPageResponse([]platform.WarehouseResponse{warehouse}, 1, platform.NormalizePageQuery(0, 20, "", "")),
		detail:  warehouse,
		created: warehouse,
		updated: warehouse,
		options: []platform.OptionResponse{{Label: "一号库", Value: "一号库"}},
	}
	permission := &recordingPermissionChecker{}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission: permission,
		Warehouse:  provider,
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/warehouses?keyword=一号&warehouseType=自有&status=正常", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var pageBody struct {
		Data platform.PageResponse[platform.WarehouseResponse] `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &pageBody); err != nil {
		t.Fatalf("decode page response: %v", err)
	}
	if len(pageBody.Data.Content) != 1 || pageBody.Data.Content[0].WarehouseCode != "WH001" || pageBody.Data.Content[0].ID != 1 {
		t.Fatalf("unexpected warehouse page: %+v", pageBody.Data)
	}
	if provider.pageKeyword != "一号" || provider.pageType != "自有" || provider.pageStatus != "正常" {
		t.Fatalf("page filters = %q/%q/%q, want 一号/自有/正常", provider.pageKeyword, provider.pageType, provider.pageStatus)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "warehouse", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/warehouses/option", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var optionBody struct {
		Data []platform.OptionResponse `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &optionBody); err != nil {
		t.Fatalf("decode option response: %v", err)
	}
	if len(optionBody.Data) != 1 || optionBody.Data[0].Label != "一号库" || optionBody.Data[0].Value != "一号库" {
		t.Fatalf("unexpected warehouse options: %+v", optionBody.Data)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "warehouse", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodGet, "/api/warehouses/1", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	if provider.detailID != 1 {
		t.Fatalf("detailID = %d, want 1", provider.detailID)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "warehouse", action: "read"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPost, "/api/warehouses", `{"warehouseCode":"WH002","warehouseName":"二号库","warehouseType":"外协","contactName":"赵六","contactPhone":"13600136000","address":"上海市嘉定区","status":"正常","remark":"备注"}`)
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "创建成功")
	if provider.createRequest.WarehouseCode != "WH002" || provider.createRequest.WarehouseType != "外协" {
		t.Fatalf("unexpected create request: %+v", provider.createRequest)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "warehouse", action: "create"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPut, "/api/warehouses/1", `{"warehouseCode":"WH001","warehouseName":"一号库","warehouseType":"自有","status":"正常"}`)
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "更新成功")
	if provider.updateID != 1 || provider.updateRequest.WarehouseName != "一号库" {
		t.Fatalf("unexpected update args: id=%d request=%+v", provider.updateID, provider.updateRequest)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "warehouse", action: "update"}})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodDelete, "/api/warehouses/1", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, "删除成功")
	if provider.deleteID != 1 {
		t.Fatalf("deleteID = %d, want 1", provider.deleteID)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{{resource: "warehouse", action: "delete"}})
}

func TestRateLimitAdminEndpoints(t *testing.T) {
	gin.SetMode(gin.TestMode)
	permission := &recordingPermissionChecker{}
	provider := &fakeRateLimitAdminProvider{
		rules: []map[string]any{{
			"id":                 "1",
			"rule_key":           "global_default",
			"rule_type":          "GLOBAL",
			"rate":               100.0,
			"capacity":           150,
			"tokens_per_request": 1,
			"priority":           200,
			"enabled":            true,
			"created_at":         int64(1767225600000),
			"updated_at":         nil,
		}},
	}
	router := newServicesTestRouter(fakeAuthProvider{
		principal: platform.AuthenticatedPrincipal{UserID: 1, LoginName: "admin"},
	}, RouterServices{
		Permission: permission,
		RateLimit:  provider,
	})

	response := httptest.NewRecorder()
	request := authenticatedJSONRequest(http.MethodGet, "/api/admin/rate-limit/rules", "")
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var listBody struct {
		Data []map[string]any `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &listBody); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if len(listBody.Data) != 1 || listBody.Data[0]["id"] != "1" || listBody.Data[0]["rule_key"] != "global_default" {
		t.Fatalf("unexpected rate-limit rules: %+v", listBody.Data)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{
		{resource: "general-setting", action: "read"},
	})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPut, "/api/admin/rate-limit/rules/1", `{"enabled":false,"rate":12.5,"ignored":true}`)
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	var updateBody map[string]any
	if err := json.Unmarshal(response.Body.Bytes(), &updateBody); err != nil {
		t.Fatalf("decode update response: %v", err)
	}
	if _, ok := updateBody["data"]; ok {
		t.Fatalf("update response should omit null data: %s", response.Body.String())
	}
	if provider.updateID != 1 || provider.updateBody["enabled"] != false || provider.updateBody["rate"] != 12.5 || provider.updateBody["ignored"] != true {
		t.Fatalf("unexpected update args: id=%d body=%+v", provider.updateID, provider.updateBody)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{
		{resource: "general-setting", action: "update"},
	})

	permission.calls = nil
	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPut, "/api/admin/rate-limit/rules/0", `{}`)
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusOK, codeSuccess.Code, codeSuccess.Message)
	if provider.updateID != 0 {
		t.Fatalf("updateID = %d, want 0", provider.updateID)
	}
	assertPermissionCalls(t, permission.calls, []permissionCall{
		{resource: "general-setting", action: "update"},
	})

	response = httptest.NewRecorder()
	request = authenticatedJSONRequest(http.MethodPut, "/api/admin/rate-limit/rules/1", `null`)
	router.ServeHTTP(response, request)
	assertAPIResponse(t, response, http.StatusInternalServerError, codeInternalError.Code, "系统异常")
}

func TestNotFoundUsesApiEnvelope(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := NewRouter(
		config.Config{AppName: "leo", Database: config.DatabaseConfig{ValidationTimeout: time.Second}},
		slog.New(slog.DiscardHandler),
		fakeHealthChecker{},
		fakeSetupStatusProvider{},
		fakeCaptchaGenerator{},
		fakeAuthProvider{},
	)

	response := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodGet, "/api/missing", nil)
	router.ServeHTTP(response, request)

	if response.Code != http.StatusNotFound {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusNotFound)
	}

	var body struct {
		Code int `json:"code"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Code != codeNotFound.Code {
		t.Fatalf("code = %d, want %d", body.Code, codeNotFound.Code)
	}
}

type fakeHealthChecker struct {
	health platform.Health
}

func (f fakeHealthChecker) Check(context.Context) platform.Health {
	return f.health
}

type fakeSetupStatusProvider struct {
	status platform.SetupStatus
	err    error
}

func (f fakeSetupStatusProvider) Status(context.Context) (platform.SetupStatus, error) {
	return f.status, f.err
}

type fakeCaptchaGenerator struct {
	captcha platform.Captcha
	err     error
}

func (f fakeCaptchaGenerator) Generate(context.Context) (platform.Captcha, error) {
	return f.captcha, f.err
}

type fakeSetupInitializer struct {
	initializeResponse platform.InitialSetupSubmitResponse
	initializeErr      error
	initializeRequest  platform.InitialSetupSubmitRequest
	totpResponse       platform.TotpSetupResponse
	totpErr            error
	adminResponse      platform.InitialSetupSubmitResponse
	adminErr           error
	companyResponse    platform.InitialSetupSubmitResponse
	companyErr         error
}

func (f *fakeSetupInitializer) Initialize(_ context.Context, req platform.InitialSetupSubmitRequest) (platform.InitialSetupSubmitResponse, error) {
	f.initializeRequest = req
	return f.initializeResponse, f.initializeErr
}

func (f *fakeSetupInitializer) SetupAdminTotp(context.Context, platform.InitialSetupTotpSetupRequest) (platform.TotpSetupResponse, error) {
	return f.totpResponse, f.totpErr
}

func (f *fakeSetupInitializer) ConfigureAdmin(context.Context, platform.InitialSetupAdminSubmitRequest) (platform.InitialSetupSubmitResponse, error) {
	return f.adminResponse, f.adminErr
}

func (f *fakeSetupInitializer) ConfigureCompany(context.Context, platform.InitialSetupCompanyRequest) (platform.InitialSetupSubmitResponse, error) {
	return f.companyResponse, f.companyErr
}

type fakeAuthProvider struct {
	loginResponse     platform.LoginResponseBody
	loginErr          error
	login2FAResponse  platform.TokenResponse
	login2FAErr       error
	refreshResponse   platform.TokenResponse
	refreshErr        error
	logoutErr         error
	principal         platform.AuthenticatedPrincipal
	authenticateErr   error
	securityStatus    platform.CurrentUserSecurityResponse
	securityErr       error
	verifyTOTPErr     error
	changePasswordErr error
	setup2FA          accountSecuritySetupResponse
	setup2FAErr       error
	enable2FA         platform.CurrentUserSecurityResponse
	enable2FAErr      error
	disable2FA        platform.CurrentUserSecurityResponse
	disable2FAErr     error
}

func (f fakeAuthProvider) Login(context.Context, platform.LoginRequest, string, string, platform.CaptchaVerifier) (platform.LoginResponseBody, error) {
	return f.loginResponse, f.loginErr
}

func (f fakeAuthProvider) Login2FA(context.Context, platform.Login2FARequest, string, string) (platform.TokenResponse, error) {
	return f.login2FAResponse, f.login2FAErr
}

func (f fakeAuthProvider) Refresh(context.Context, string, string, string) (platform.TokenResponse, error) {
	return f.refreshResponse, f.refreshErr
}

func (f fakeAuthProvider) Logout(context.Context, string) error {
	return f.logoutErr
}

func (f fakeAuthProvider) AuthenticateAccessToken(context.Context, string) (platform.AuthenticatedPrincipal, error) {
	return f.principal, f.authenticateErr
}

func (f fakeAuthProvider) CurrentUserSecurityStatus(context.Context, int64) (platform.CurrentUserSecurityResponse, error) {
	return f.securityStatus, f.securityErr
}

func (f fakeAuthProvider) VerifyCurrentUserTOTP(context.Context, int64, string) error {
	return f.verifyTOTPErr
}

func (f fakeAuthProvider) ChangeCurrentUserPassword(context.Context, int64, string, string) error {
	return f.changePasswordErr
}

func (f fakeAuthProvider) SetupCurrentUser2FA(context.Context, int64) (string, string, error) {
	return f.setup2FA.QRCodeBase64, f.setup2FA.Secret, f.setup2FAErr
}

func (f fakeAuthProvider) EnableCurrentUser2FA(context.Context, int64, string) (platform.CurrentUserSecurityResponse, error) {
	return f.enable2FA, f.enable2FAErr
}

func (f fakeAuthProvider) DisableCurrentUser2FA(context.Context, int64, string) (platform.CurrentUserSecurityResponse, error) {
	return f.disable2FA, f.disable2FAErr
}

type accountSecuritySetupResponse struct {
	QRCodeBase64 string `json:"qrCodeBase64"`
	Secret       string `json:"secret"`
}

func newAccountSecurityTestRouter(auth fakeAuthProvider) http.Handler {
	return NewRouter(
		config.Config{AppName: "leo", Database: config.DatabaseConfig{ValidationTimeout: time.Second}},
		slog.New(slog.DiscardHandler),
		fakeHealthChecker{},
		fakeSetupStatusProvider{},
		fakeCaptchaGenerator{},
		auth,
	)
}

func newServicesTestRouter(auth fakeAuthProvider, services RouterServices) http.Handler {
	return NewRouterWithServices(
		config.Config{AppName: "leo", Database: config.DatabaseConfig{ValidationTimeout: time.Second}},
		slog.New(slog.DiscardHandler),
		fakeHealthChecker{},
		fakeSetupStatusProvider{},
		fakeCaptchaGenerator{},
		auth,
		services,
	)
}

type fakePermissionChecker struct {
	err error
}

func (f fakePermissionChecker) Require(context.Context, int64, string, string) error {
	return f.err
}

type permissionCall struct {
	resource string
	action   string
}

type recordingPermissionChecker struct {
	calls           []permissionCall
	failures        map[string]error
	dataScopeCalls  []permissionCall
	dataScopeResult platform.DataScope
	dataScopeErr    error
}

func (r *recordingPermissionChecker) Require(_ context.Context, _ int64, resource string, action string) error {
	r.calls = append(r.calls, permissionCall{resource: resource, action: action})
	if r.failures != nil {
		if err := r.failures[resource+":"+action]; err != nil {
			return err
		}
	}
	return nil
}

func (r *recordingPermissionChecker) DataScope(_ context.Context, userID int64, resource string, action string) (platform.DataScope, error) {
	r.calls = append(r.calls, permissionCall{resource: resource, action: action})
	r.dataScopeCalls = append(r.dataScopeCalls, permissionCall{resource: resource, action: action})
	if r.dataScopeErr != nil {
		return platform.DataScope{}, r.dataScopeErr
	}
	if r.failures != nil {
		if err := r.failures[resource+":"+action]; err != nil {
			return platform.DataScope{}, err
		}
	}
	if r.dataScopeResult.UserID != 0 || r.dataScopeResult.Scope != "" {
		return r.dataScopeResult, nil
	}
	return platform.DataScopeAll(userID, resource, action), nil
}

type fakeDashboardProvider struct {
	summary platform.DashboardSummary
	err     error
}

func (f fakeDashboardProvider) Summary(context.Context, int64) (platform.DashboardSummary, error) {
	return f.summary, f.err
}

type fakeDepartmentProvider struct {
	page      platform.PageResponse[platform.DepartmentResponse]
	options   []platform.DepartmentOptionResponse
	detail    platform.DepartmentResponse
	created   platform.DepartmentResponse
	updated   platform.DepartmentResponse
	err       error
	deletedID int64
}

func (f fakeDepartmentProvider) Page(context.Context, platform.PageQuery, string, string) (platform.PageResponse[platform.DepartmentResponse], error) {
	return f.page, f.err
}

func (f fakeDepartmentProvider) Options(context.Context) ([]platform.DepartmentOptionResponse, error) {
	return f.options, f.err
}

func (f fakeDepartmentProvider) Detail(context.Context, int64) (platform.DepartmentResponse, error) {
	return f.detail, f.err
}

func (f *fakeDepartmentProvider) Create(context.Context, platform.DepartmentRequest) (platform.DepartmentResponse, error) {
	return f.created, f.err
}

func (f *fakeDepartmentProvider) Update(context.Context, int64, platform.DepartmentRequest) (platform.DepartmentResponse, error) {
	return f.updated, f.err
}

func (f *fakeDepartmentProvider) Delete(_ context.Context, id int64) error {
	f.deletedID = id
	return f.err
}

type fakeOperationLogProvider struct {
	page platform.PageResponse[platform.OperationLogResponse]
	err  error
}

func (f fakeOperationLogProvider) Page(context.Context, platform.PageQuery, platform.OperationLogFilter) (platform.PageResponse[platform.OperationLogResponse], error) {
	return f.page, f.err
}

type fakeIoReportProvider struct {
	page          platform.PageResponse[platform.IoReportResponse]
	err           error
	pageQuery     platform.PageQuery
	keyword       string
	businessType  string
	startDate     string
	endDate       string
	pageDataScope platform.DataScope
}

func (f *fakeIoReportProvider) Page(ctx context.Context, query platform.PageQuery, filter platform.IoReportFilter) (platform.PageResponse[platform.IoReportResponse], error) {
	f.pageQuery = query
	f.keyword = filter.Keyword
	f.businessType = filter.BusinessType
	f.startDate = filter.StartDate
	f.endDate = filter.EndDate
	f.pageDataScope, _ = platform.CurrentDataScope(ctx)
	if f.businessType != "" && f.businessType != "采购入库" && f.businessType != "销售出库" {
		return platform.PageResponse[platform.IoReportResponse]{}, platform.NewAuthError(platform.AuthErrorValidation, "businessType 不合法")
	}
	if f.startDate != "" && f.endDate != "" {
		startDate, _ := time.Parse(time.DateOnly, f.startDate)
		endDate, _ := time.Parse(time.DateOnly, f.endDate)
		if startDate.After(endDate) {
			return platform.PageResponse[platform.IoReportResponse]{}, platform.NewAuthError(platform.AuthErrorValidation, "startDate 不能晚于 endDate")
		}
	}
	return f.page, f.err
}

type fakeInventoryReportProvider struct {
	page                platform.PageResponse[platform.InventoryReportResponse]
	exportFile          platform.FileDownloadResponse
	err                 error
	exportErr           error
	pageQuery           platform.PageQuery
	keyword             string
	warehouseName       string
	category            string
	pageDataScope       platform.DataScope
	exportKeyword       string
	exportWarehouseName string
	exportCategory      string
	exportDataScope     platform.DataScope
}

func (f *fakeInventoryReportProvider) Page(ctx context.Context, query platform.PageQuery, filter platform.InventoryReportFilter) (platform.PageResponse[platform.InventoryReportResponse], error) {
	f.pageQuery = query
	f.keyword = filter.Keyword
	f.warehouseName = filter.WarehouseName
	f.category = filter.Category
	f.pageDataScope, _ = platform.CurrentDataScope(ctx)
	return f.page, f.err
}

func (f *fakeInventoryReportProvider) ExportExcel(ctx context.Context, keyword, warehouseName, category string) (platform.FileDownloadResponse, error) {
	f.exportKeyword = keyword
	f.exportWarehouseName = warehouseName
	f.exportCategory = category
	f.exportDataScope, _ = platform.CurrentDataScope(ctx)
	return f.exportFile, f.exportErr
}

type fakePendingInvoiceReceiptReportProvider struct {
	page          platform.PageResponse[platform.PendingInvoiceReceiptReportResponse]
	err           error
	pageQuery     platform.PageQuery
	keyword       string
	supplierName  string
	startDate     string
	endDate       string
	pageDataScope platform.DataScope
}

func (f *fakePendingInvoiceReceiptReportProvider) Page(ctx context.Context, query platform.PageQuery, filter platform.PendingInvoiceReceiptReportFilter) (platform.PageResponse[platform.PendingInvoiceReceiptReportResponse], error) {
	f.pageQuery = query
	f.keyword = filter.Keyword
	f.supplierName = filter.SupplierName
	f.startDate = filter.StartDate
	f.endDate = filter.EndDate
	f.pageDataScope, _ = platform.CurrentDataScope(ctx)
	return f.page, f.err
}

type fakeGeneralSettingProvider struct {
	page      platform.PageResponse[platform.GeneralSettingResponse]
	display   []platform.GeneralSettingResponse
	client    []platform.GeneralSettingResponse
	statement platform.StatementGeneratorRulesResponse
	detail    platform.NoRuleResponse
	next      platform.NoRuleGenerateResponse
	created   platform.NoRuleResponse
	updated   platform.NoRuleResponse
	err       error
	deletedID int64
}

func (f fakeGeneralSettingProvider) Page(context.Context, platform.PageQuery, string, string) (platform.PageResponse[platform.GeneralSettingResponse], error) {
	return f.page, f.err
}

func (f fakeGeneralSettingProvider) PublicDisplaySwitches(context.Context) ([]platform.GeneralSettingResponse, error) {
	return f.display, f.err
}

func (f fakeGeneralSettingProvider) PublicClientSettings(context.Context) ([]platform.GeneralSettingResponse, error) {
	return f.client, f.err
}

func (f fakeGeneralSettingProvider) StatementGeneratorRules(context.Context) (platform.StatementGeneratorRulesResponse, error) {
	return f.statement, f.err
}

func (f fakeGeneralSettingProvider) Detail(context.Context, int64) (platform.NoRuleResponse, error) {
	return f.detail, f.err
}

func (f fakeGeneralSettingProvider) NextNumber(context.Context, string) (platform.NoRuleGenerateResponse, error) {
	return f.next, f.err
}

func (f *fakeGeneralSettingProvider) Create(context.Context, platform.NoRuleRequest) (platform.NoRuleResponse, error) {
	return f.created, f.err
}

func (f *fakeGeneralSettingProvider) Update(context.Context, int64, platform.NoRuleRequest) (platform.NoRuleResponse, error) {
	return f.updated, f.err
}

func (f *fakeGeneralSettingProvider) Delete(_ context.Context, id int64) error {
	f.deletedID = id
	return f.err
}

type fakeUploadRuleProvider struct {
	detail  platform.UploadRuleResponse
	updated platform.UploadRuleResponse
	err     error
}

func (f fakeUploadRuleProvider) Detail(context.Context, string) (platform.UploadRuleResponse, error) {
	return f.detail, f.err
}

func (f *fakeUploadRuleProvider) Update(context.Context, string, platform.UploadRuleRequest) (platform.UploadRuleResponse, error) {
	return f.updated, f.err
}

type fakeSecurityKeyProvider struct {
	overview platform.SecurityKeyOverviewResponse
	jwt      platform.SecurityKeyRotateResponse
	totp     platform.SecurityKeyRotateResponse
	err      error
}

func (f fakeSecurityKeyProvider) Overview(context.Context) (platform.SecurityKeyOverviewResponse, error) {
	return f.overview, f.err
}

func (f fakeSecurityKeyProvider) RotateJWT(context.Context) (platform.SecurityKeyRotateResponse, error) {
	return f.jwt, f.err
}

func (f fakeSecurityKeyProvider) RotateTOTP(context.Context) (platform.SecurityKeyRotateResponse, error) {
	return f.totp, f.err
}

type fakeMenuProvider struct {
	tree []platform.MenuNode
	err  error
}

func (f fakeMenuProvider) Tree(context.Context, int64) ([]platform.MenuNode, error) {
	return f.tree, f.err
}

type fakeCompanyProvider struct {
	current *platform.CompanySetting
	page    platform.PageResponse[platform.CompanySetting]
	detail  platform.CompanySetting
	created platform.CompanySetting
	updated platform.CompanySetting
	saved   platform.CompanySetting
	deleted bool
	err     error
}

func (f fakeCompanyProvider) Page(context.Context, platform.PageQuery, string, string) (platform.PageResponse[platform.CompanySetting], error) {
	return f.page, f.err
}

func (f fakeCompanyProvider) Detail(context.Context, int64) (platform.CompanySetting, error) {
	return f.detail, f.err
}

func (f fakeCompanyProvider) Current(context.Context) (*platform.CompanySetting, error) {
	return f.current, f.err
}

func (f fakeCompanyProvider) SaveCurrent(context.Context, platform.CompanySetting) (platform.CompanySetting, error) {
	return f.saved, f.err
}

func (f fakeCompanyProvider) Create(context.Context, platform.CompanySetting) (platform.CompanySetting, error) {
	return f.created, f.err
}

func (f fakeCompanyProvider) Update(context.Context, int64, platform.CompanySetting) (platform.CompanySetting, error) {
	return f.updated, f.err
}

func (f fakeCompanyProvider) Delete(context.Context, int64) error {
	return f.err
}

type fakeDatabaseProvider struct {
	status     platform.DatabaseStatus
	monitoring platform.DatabaseMonitoring
}

func (f fakeDatabaseProvider) Status(context.Context) platform.DatabaseStatus {
	return f.status
}

func (f fakeDatabaseProvider) Monitoring(context.Context) platform.DatabaseMonitoring {
	return f.monitoring
}

type fakeGlobalSearcher struct {
	results []platform.GlobalSearchResult
	err     error
}

func (f fakeGlobalSearcher) Search(context.Context, int64, string, int, []string) ([]platform.GlobalSearchResult, error) {
	return f.results, f.err
}

type fakePrintTemplateProvider struct {
	list           []platform.PrintTemplateResponse
	billType       string
	created        platform.PrintTemplateResponse
	updated        platform.PrintTemplateResponse
	uploaded       platform.PrintTemplateResponse
	err            error
	listBillType   string
	createRequest  platform.PrintTemplateRequest
	updateID       int64
	updateRequest  platform.PrintTemplateRequest
	uploadID       int64
	uploadFilename string
	uploadContent  []byte
	deleteID       int64
}

func (f *fakePrintTemplateProvider) ListByBillType(_ context.Context, billType string) ([]platform.PrintTemplateResponse, error) {
	f.listBillType = billType
	return f.list, f.err
}

func (f *fakePrintTemplateProvider) GetBillType(context.Context, int64) (string, error) {
	return f.billType, f.err
}

func (f *fakePrintTemplateProvider) Create(_ context.Context, request platform.PrintTemplateRequest) (platform.PrintTemplateResponse, error) {
	f.createRequest = request
	return f.created, f.err
}

func (f *fakePrintTemplateProvider) Update(_ context.Context, id int64, request platform.PrintTemplateRequest) (platform.PrintTemplateResponse, error) {
	f.updateID = id
	f.updateRequest = request
	return f.updated, f.err
}

func (f *fakePrintTemplateProvider) UploadJSON(_ context.Context, id int64, filename string, content []byte) (platform.PrintTemplateResponse, error) {
	f.uploadID = id
	f.uploadFilename = filename
	f.uploadContent = content
	return f.uploaded, f.err
}

func (f *fakePrintTemplateProvider) Delete(_ context.Context, id int64) error {
	f.deleteID = id
	return f.err
}

type fakeRateLimitAdminProvider struct {
	rules      []map[string]any
	err        error
	updateID   int64
	updateBody map[string]any
}

func (f *fakeRateLimitAdminProvider) ListRules(context.Context) ([]map[string]any, error) {
	return f.rules, f.err
}

func (f *fakeRateLimitAdminProvider) UpdateRule(_ context.Context, id int64, body map[string]any) error {
	f.updateID = id
	f.updateBody = body
	return f.err
}

type fakeMaterialCategoryProvider struct {
	page          platform.PageResponse[platform.MaterialCategoryResponse]
	detail        platform.MaterialCategoryResponse
	created       platform.MaterialCategoryResponse
	updated       platform.MaterialCategoryResponse
	options       []platform.MaterialCategoryOptionResponse
	err           error
	pageKeyword   string
	pageStatus    string
	detailID      int64
	createRequest platform.MaterialCategoryRequest
	updateID      int64
	updateRequest platform.MaterialCategoryRequest
	deleteID      int64
}

func (f *fakeMaterialCategoryProvider) Page(_ context.Context, _ platform.PageQuery, keyword string, status string) (platform.PageResponse[platform.MaterialCategoryResponse], error) {
	f.pageKeyword = keyword
	f.pageStatus = status
	return f.page, f.err
}

func (f *fakeMaterialCategoryProvider) Detail(_ context.Context, id int64) (platform.MaterialCategoryResponse, error) {
	f.detailID = id
	return f.detail, f.err
}

func (f *fakeMaterialCategoryProvider) Create(_ context.Context, request platform.MaterialCategoryRequest) (platform.MaterialCategoryResponse, error) {
	f.createRequest = request
	return f.created, f.err
}

func (f *fakeMaterialCategoryProvider) Update(_ context.Context, id int64, request platform.MaterialCategoryRequest) (platform.MaterialCategoryResponse, error) {
	f.updateID = id
	f.updateRequest = request
	return f.updated, f.err
}

func (f *fakeMaterialCategoryProvider) Delete(_ context.Context, id int64) error {
	f.deleteID = id
	return f.err
}

func (f *fakeMaterialCategoryProvider) Options(context.Context) ([]platform.MaterialCategoryOptionResponse, error) {
	return f.options, f.err
}

type fakeCustomerProvider struct {
	page          platform.PageResponse[platform.CustomerResponse]
	detail        platform.CustomerResponse
	created       platform.CustomerResponse
	updated       platform.CustomerResponse
	options       []platform.CustomerOptionResponse
	err           error
	pageKeyword   string
	pageStatus    string
	pageDataScope platform.DataScope
	detailID      int64
	createRequest platform.CustomerRequest
	updateID      int64
	updateRequest platform.CustomerRequest
	deleteID      int64
}

func (f *fakeCustomerProvider) Page(ctx context.Context, _ platform.PageQuery, keyword string, status string) (platform.PageResponse[platform.CustomerResponse], error) {
	f.pageKeyword = keyword
	f.pageStatus = status
	f.pageDataScope, _ = platform.CurrentDataScope(ctx)
	return f.page, f.err
}

func (f *fakeCustomerProvider) Detail(_ context.Context, id int64) (platform.CustomerResponse, error) {
	f.detailID = id
	return f.detail, f.err
}

func (f *fakeCustomerProvider) Create(_ context.Context, request platform.CustomerRequest) (platform.CustomerResponse, error) {
	f.createRequest = request
	return f.created, f.err
}

func (f *fakeCustomerProvider) Update(_ context.Context, id int64, request platform.CustomerRequest) (platform.CustomerResponse, error) {
	f.updateID = id
	f.updateRequest = request
	return f.updated, f.err
}

func (f *fakeCustomerProvider) Delete(_ context.Context, id int64) error {
	f.deleteID = id
	return f.err
}

func (f *fakeCustomerProvider) Options(context.Context) ([]platform.CustomerOptionResponse, error) {
	return f.options, f.err
}

type fakeProjectProvider struct {
	page          platform.PageResponse[platform.ProjectResponse]
	detail        platform.ProjectResponse
	created       platform.ProjectResponse
	updated       platform.ProjectResponse
	err           error
	pageQuery     platform.PageQuery
	pageKeyword   string
	pageStatus    string
	pageDataScope platform.DataScope
	detailID      int64
	createRequest platform.ProjectRequest
	updateID      int64
	updateRequest platform.ProjectRequest
	deleteID      int64
}

func (f *fakeProjectProvider) Page(ctx context.Context, query platform.PageQuery, keyword string, status string) (platform.PageResponse[platform.ProjectResponse], error) {
	f.pageQuery = query
	f.pageKeyword = keyword
	f.pageStatus = status
	f.pageDataScope, _ = platform.CurrentDataScope(ctx)
	return f.page, f.err
}

func (f *fakeProjectProvider) Detail(_ context.Context, id int64) (platform.ProjectResponse, error) {
	f.detailID = id
	return f.detail, f.err
}

func (f *fakeProjectProvider) Create(_ context.Context, request platform.ProjectRequest) (platform.ProjectResponse, error) {
	f.createRequest = request
	return f.created, f.err
}

func (f *fakeProjectProvider) Update(_ context.Context, id int64, request platform.ProjectRequest) (platform.ProjectResponse, error) {
	f.updateID = id
	f.updateRequest = request
	return f.updated, f.err
}

func (f *fakeProjectProvider) Delete(_ context.Context, id int64) error {
	f.deleteID = id
	return f.err
}

type fakeCarrierProvider struct {
	page          platform.PageResponse[platform.CarrierResponse]
	detail        platform.CarrierResponse
	created       platform.CarrierResponse
	updated       platform.CarrierResponse
	options       []platform.CarrierOptionResponse
	err           error
	pageKeyword   string
	pageStatus    string
	detailID      int64
	createRequest platform.CarrierRequest
	updateID      int64
	updateRequest platform.CarrierRequest
	deleteID      int64
}

func (f *fakeCarrierProvider) Page(_ context.Context, _ platform.PageQuery, keyword string, status string) (platform.PageResponse[platform.CarrierResponse], error) {
	f.pageKeyword = keyword
	f.pageStatus = status
	return f.page, f.err
}

func (f *fakeCarrierProvider) Detail(_ context.Context, id int64) (platform.CarrierResponse, error) {
	f.detailID = id
	return f.detail, f.err
}

func (f *fakeCarrierProvider) Create(_ context.Context, request platform.CarrierRequest) (platform.CarrierResponse, error) {
	f.createRequest = request
	return f.created, f.err
}

func (f *fakeCarrierProvider) Update(_ context.Context, id int64, request platform.CarrierRequest) (platform.CarrierResponse, error) {
	f.updateID = id
	f.updateRequest = request
	return f.updated, f.err
}

func (f *fakeCarrierProvider) Delete(_ context.Context, id int64) error {
	f.deleteID = id
	return f.err
}

func (f *fakeCarrierProvider) Options(context.Context) ([]platform.CarrierOptionResponse, error) {
	return f.options, f.err
}

type fakeWarehouseProvider struct {
	page          platform.PageResponse[platform.WarehouseResponse]
	detail        platform.WarehouseResponse
	created       platform.WarehouseResponse
	updated       platform.WarehouseResponse
	options       []platform.OptionResponse
	err           error
	pageKeyword   string
	pageType      string
	pageStatus    string
	detailID      int64
	createRequest platform.WarehouseRequest
	updateID      int64
	updateRequest platform.WarehouseRequest
	deleteID      int64
}

func (f *fakeWarehouseProvider) Page(_ context.Context, _ platform.PageQuery, keyword string, warehouseType string, status string) (platform.PageResponse[platform.WarehouseResponse], error) {
	f.pageKeyword = keyword
	f.pageType = warehouseType
	f.pageStatus = status
	return f.page, f.err
}

func (f *fakeWarehouseProvider) Detail(_ context.Context, id int64) (platform.WarehouseResponse, error) {
	f.detailID = id
	return f.detail, f.err
}

func (f *fakeWarehouseProvider) Create(_ context.Context, request platform.WarehouseRequest) (platform.WarehouseResponse, error) {
	f.createRequest = request
	return f.created, f.err
}

func (f *fakeWarehouseProvider) Update(_ context.Context, id int64, request platform.WarehouseRequest) (platform.WarehouseResponse, error) {
	f.updateID = id
	f.updateRequest = request
	return f.updated, f.err
}

func (f *fakeWarehouseProvider) Delete(_ context.Context, id int64) error {
	f.deleteID = id
	return f.err
}

func (f *fakeWarehouseProvider) Options(context.Context) ([]platform.OptionResponse, error) {
	return f.options, f.err
}

type fakePermissionEntryProvider struct {
	page   platform.PageResponse[platform.PermissionEntryResponse]
	detail platform.PermissionEntryResponse
	err    error
}

func (f fakePermissionEntryProvider) Page(context.Context, platform.PageQuery, string) (platform.PageResponse[platform.PermissionEntryResponse], error) {
	return f.page, f.err
}

func (f fakePermissionEntryProvider) Detail(context.Context, int64) (platform.PermissionEntryResponse, error) {
	return f.detail, f.err
}

type fakeRoleSettingProvider struct {
	page        platform.PageResponse[platform.RoleSettingResponse]
	detail      platform.RoleSettingResponse
	created     platform.RoleSettingResponse
	updated     platform.RoleSettingResponse
	permissions []platform.RolePermissionItem
	options     []platform.MenuNode
	templates   []platform.RoleTemplate
	err         error
	deletedID   int64
	savedID     int64
}

func (f fakeRoleSettingProvider) Page(context.Context, platform.PageQuery, string, string) (platform.PageResponse[platform.RoleSettingResponse], error) {
	return f.page, f.err
}

func (f fakeRoleSettingProvider) Detail(context.Context, int64) (platform.RoleSettingResponse, error) {
	return f.detail, f.err
}

func (f *fakeRoleSettingProvider) Create(context.Context, int64, platform.RoleSettingRequest) (platform.RoleSettingResponse, error) {
	return f.created, f.err
}

func (f *fakeRoleSettingProvider) Update(context.Context, int64, int64, platform.RoleSettingRequest) (platform.RoleSettingResponse, error) {
	return f.updated, f.err
}

func (f *fakeRoleSettingProvider) Delete(_ context.Context, id int64) error {
	f.deletedID = id
	return f.err
}

func (f fakeRoleSettingProvider) Permissions(context.Context, int64) ([]platform.RolePermissionItem, error) {
	return f.permissions, f.err
}

func (f *fakeRoleSettingProvider) SavePermissions(_ context.Context, _ int64, id int64, _ []platform.RolePermissionItem) error {
	f.savedID = id
	return f.err
}

func (f fakeRoleSettingProvider) PermissionOptions(context.Context) ([]platform.MenuNode, error) {
	return f.options, f.err
}

func (f fakeRoleSettingProvider) Templates(context.Context) ([]platform.RoleTemplate, error) {
	return f.templates, f.err
}

func authenticatedJSONRequest(method string, target string, body string) *http.Request {
	request := httptest.NewRequest(method, target, bytes.NewBufferString(body))
	request.Header.Set("Authorization", "Bearer access-token")
	request.Header.Set("Content-Type", "application/json")
	return request
}

func assertAPIResponse(t *testing.T, response *httptest.ResponseRecorder, wantStatus int, wantCode int, wantMsg string) {
	t.Helper()
	if response.Code != wantStatus {
		t.Fatalf("status = %d, want %d; body = %s", response.Code, wantStatus, response.Body.String())
	}
	var body struct {
		Code    int    `json:"code"`
		Message string `json:"message"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Code != wantCode || body.Message != wantMsg {
		t.Fatalf("unexpected response: %+v, want code=%d message=%q", body, wantCode, wantMsg)
	}
}

func assertPermissionCalls(t *testing.T, got []permissionCall, want []permissionCall) {
	t.Helper()
	if len(got) != len(want) {
		t.Fatalf("permission calls = %+v, want %+v", got, want)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("permission call[%d] = %+v, want %+v; all calls=%+v", i, got[i], want[i], got)
		}
	}
}

func strPtr(value string) *string {
	return &value
}

func accountSecurityStatus(totpEnabled bool) platform.CurrentUserSecurityResponse {
	return platform.CurrentUserSecurityResponse{
		ID:               1,
		LoginName:        "admin",
		UserName:         "管理员",
		TotpEnabled:      totpEnabled,
		ForceTotpSetup:   false,
		ForbidDisable2FA: false,
	}
}

func assertSecurityStatusData(t *testing.T, response *httptest.ResponseRecorder, wantTotpEnabled bool) {
	t.Helper()
	var body struct {
		Data struct {
			ID          int64  `json:"id"`
			LoginName   string `json:"loginName"`
			TotpEnabled bool   `json:"totpEnabled"`
		} `json:"data"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Data.ID != 1 || body.Data.LoginName != "admin" || body.Data.TotpEnabled != wantTotpEnabled {
		t.Fatalf("unexpected security status: %+v", body.Data)
	}
}
