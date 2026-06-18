package platform

import (
	"context"
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha1"
	"crypto/sha256"
	"database/sql"
	"encoding/base32"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"fmt"
	"net/url"
	"os"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
	"github.com/skip2/go-qrcode"
	"golang.org/x/crypto/bcrypt"
)

const (
	authLoginFailurePrefix = "auth:login:fail:"
	authLoginLockPrefix    = "auth:login:lock:"
	auth2FATempPrefix      = "auth:2fa:temp:"
	auth2FAUserTempPrefix  = "auth:2fa:user-temp:"
	auth2FATempTTL         = 5 * time.Minute

	authDefaultIssuer  = "leo-erp"
	tokenTypeBearer    = "Bearer"
	jwtSecretType      = "JWT_MASTER"
	totpSecretType     = "TOTP_MASTER"
	secretSourceDB     = "DATABASE"
	secretSourceConfig = "CONFIG_FILE"

	refreshReuseGrace       = 30 * time.Second
	defaultRefreshExpiresIn = 7 * 24 * time.Hour
	defaultAccessExpiresIn  = 10 * time.Minute

	loginFailureWindow = 15 * time.Minute
	loginLockDuration  = 15 * time.Minute
	loginMaxFailures   = 5

	accessTokenUserBlacklistPrefix    = "access_token:blacklist:user:"
	accessTokenSessionBlacklistPrefix = "access_token:blacklist:session:"
	sessionActivityPrefix             = "session:activity:"
	sessionActivityTTL                = 15 * time.Minute
	forbidDisable2FASwitch            = "SYS_FORBID_DISABLE_2FA"
	defaultTotpIssuer                 = "LeoERP"
	defaultQrCodeSize                 = 300
	totpSecretLength                  = 20
	totpSecretAlphabet                = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

	totpPeriodSeconds = int64(30)
)

type AuthService struct {
	db          *pgxpool.Pool
	redis       *redis.Client
	idGenerator *IDGenerator
	jwtSecret   string
	issuer      string
	accessTTL   time.Duration
	refreshTTL  time.Duration
}

type LoginRequest struct {
	LoginName   string
	Password    string
	CaptchaID   string
	CaptchaCode string
}

type LoginResponseBody struct {
	Requires2FA      bool              `json:"requires2fa,omitempty"`
	TempToken        string            `json:"tempToken,omitempty"`
	AccessToken      string            `json:"accessToken,omitempty"`
	RefreshToken     *string           `json:"refreshToken,omitempty"`
	TokenType        string            `json:"tokenType,omitempty"`
	ExpiresIn        int64             `json:"expiresIn,omitempty"`
	RefreshExpiresIn int64             `json:"refreshExpiresIn,omitempty"`
	User             *AuthUserResponse `json:"user,omitempty"`
}

type Login2FARequest struct {
	TempToken string
	TotpCode  string
}

type ChangePasswordRequest struct {
	CurrentPassword string
	NewPassword     string
}

type TotpSetupResponse struct {
	QrCodeBase64 string `json:"qrCodeBase64"`
	Secret       string `json:"secret"`
}

type TotpEnableRequest struct {
	TotpCode string
}

type TokenResponse struct {
	AccessToken      string           `json:"accessToken"`
	RefreshToken     string           `json:"refreshToken,omitempty"`
	TokenType        string           `json:"tokenType"`
	ExpiresIn        int64            `json:"expiresIn"`
	RefreshExpiresIn int64            `json:"refreshExpiresIn,omitempty"`
	User             AuthUserResponse `json:"user"`
}

type AuthUserResponse struct {
	ID             int64                        `json:"id"`
	LoginName      string                       `json:"loginName"`
	UserName       string                       `json:"userName,omitempty"`
	RoleName       string                       `json:"roleName,omitempty"`
	TotpEnabled    bool                         `json:"totpEnabled,omitempty"`
	ForceTotpSetup bool                         `json:"forceTotpSetup,omitempty"`
	Permissions    []ResourcePermissionResponse `json:"permissions,omitempty"`
	DataScopes     map[string]string            `json:"dataScopes,omitempty"`
}

type AuthenticatedPrincipal struct {
	UserID         int64
	LoginName      string
	SessionID      string
	IssuedAt       time.Time
	TotpEnabled    bool
	ForceTotpSetup bool
}

type CurrentUserSecurityResponse struct {
	ID               int64  `json:"id"`
	LoginName        string `json:"loginName"`
	UserName         string `json:"userName,omitempty"`
	TotpEnabled      bool   `json:"totpEnabled"`
	ForceTotpSetup   bool   `json:"forceTotpSetup"`
	ForbidDisable2FA bool   `json:"forbidDisable2fa"`
}

type ResourcePermissionResponse struct {
	Resource string   `json:"resource"`
	Actions  []string `json:"actions"`
}

type CaptchaVerifier interface {
	Verify(ctx context.Context, captchaID string, inputCode string) (bool, error)
	ShouldRequireLoginCaptcha(ctx context.Context) (bool, error)
}

type authUserRecord struct {
	ID               int64
	LoginName        string
	PasswordHash     string
	UserName         string
	TotpSecret       sql.NullString
	TotpEnabled      bool
	RequireTotpSetup bool
	Status           string
}

type refreshSessionRecord struct {
	ID                      int64
	UserID                  int64
	TokenID                 string
	TokenHash               string
	PreviousTokenHash       sql.NullString
	PreviousTokenValidUntil sql.NullTime
	ExpiresAt               time.Time
	RevokedAt               sql.NullTime
	RevokeReason            sql.NullString
}

type secretMaterial struct {
	Source    string
	Version   int
	Value     string
	RetiredAt sql.NullTime
}

func NewAuthService(db *pgxpool.Pool, redis *redis.Client, jwtSecret string, issuer string, accessTTL, refreshTTL time.Duration, machineID int64) *AuthService {
	issuer = strings.TrimSpace(issuer)
	if issuer == "" {
		issuer = authDefaultIssuer
	}
	if accessTTL <= 0 {
		accessTTL = defaultAccessExpiresIn
	}
	if refreshTTL <= 0 {
		refreshTTL = defaultRefreshExpiresIn
	}
	return &AuthService{
		db:          db,
		redis:       redis,
		idGenerator: NewIDGenerator(machineID),
		jwtSecret:   strings.TrimSpace(jwtSecret),
		issuer:      issuer,
		accessTTL:   accessTTL,
		refreshTTL:  refreshTTL,
	}
}

func (s *AuthService) Login(ctx context.Context, req LoginRequest, clientIP, userAgent string, captcha CaptchaVerifier) (LoginResponseBody, error) {
	loginName := strings.TrimSpace(req.LoginName)
	if loginName == "" {
		return LoginResponseBody{}, NewAuthError(AuthErrorValidation, "登录账号不能为空")
	}
	if strings.TrimSpace(req.Password) == "" {
		return LoginResponseBody{}, NewAuthError(AuthErrorValidation, "密码不能为空")
	}
	if err := s.ensureReady(); err != nil {
		return LoginResponseBody{}, err
	}
	if err := s.ensureLoginAllowed(ctx, loginName); err != nil {
		return LoginResponseBody{}, err
	}
	if err := s.verifyCaptcha(ctx, req, captcha); err != nil {
		return LoginResponseBody{}, err
	}

	user, err := s.findUserByLoginName(ctx, loginName)
	if err != nil {
		s.recordFailure(ctx, loginName)
		return LoginResponseBody{}, NewAuthError(AuthErrorUnauthorized, "账号或密码错误")
	}
	if user.Status != "NORMAL" {
		return LoginResponseBody{}, NewAuthError(AuthErrorForbidden, "账户已禁用")
	}
	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(req.Password)); err != nil {
		s.recordFailure(ctx, loginName)
		return LoginResponseBody{}, NewAuthError(AuthErrorUnauthorized, "账号或密码错误")
	}

	if user.TotpEnabled && strings.TrimSpace(user.TotpSecret.String) != "" {
		tempToken, err := s.issueTwoFactorTempToken(ctx, user.ID)
		if err != nil {
			return LoginResponseBody{}, err
		}
		s.clearFailures(ctx, loginName)
		return LoginResponseBody{Requires2FA: true, TempToken: tempToken}, nil
	}

	token, err := s.issueTokenResponse(ctx, user, clientIP, userAgent)
	if err != nil {
		return LoginResponseBody{}, err
	}
	s.clearFailures(ctx, loginName)
	return loginBodyFromToken(token), nil
}

func (s *AuthService) Login2FA(ctx context.Context, req Login2FARequest, clientIP, userAgent string) (TokenResponse, error) {
	if strings.TrimSpace(req.TempToken) == "" {
		return TokenResponse{}, NewAuthError(AuthErrorValidation, "临时令牌不能为空")
	}
	if strings.TrimSpace(req.TotpCode) == "" {
		return TokenResponse{}, NewAuthError(AuthErrorValidation, "验证码不能为空")
	}
	if err := s.ensureReady(); err != nil {
		return TokenResponse{}, err
	}

	userIDText, err := s.redis.Get(ctx, auth2FATempPrefix+strings.TrimSpace(req.TempToken)).Result()
	if err != nil {
		if errors.Is(err, redis.Nil) {
			return TokenResponse{}, NewAuthError(AuthErrorUnauthorized, "2FA验证已过期，请重新登录")
		}
		return TokenResponse{}, err
	}
	userID, err := strconv.ParseInt(userIDText, 10, 64)
	if err != nil {
		return TokenResponse{}, NewAuthError(AuthErrorUnauthorized, "2FA验证已过期，请重新登录")
	}
	if err := s.redis.Del(ctx, auth2FATempPrefix+strings.TrimSpace(req.TempToken), auth2FAUserTempPrefix+userIDText).Err(); err != nil {
		return TokenResponse{}, err
	}

	user, err := s.findUserByID(ctx, userID)
	if err != nil {
		return TokenResponse{}, NewAuthError(AuthErrorUnauthorized, "用户不存在")
	}
	if user.Status != "NORMAL" {
		return TokenResponse{}, NewAuthError(AuthErrorForbidden, "账户已禁用")
	}
	secret, err := s.decryptTotpSecret(ctx, user.TotpSecret.String)
	if err != nil {
		return TokenResponse{}, err
	}
	if !verifyTOTP(secret, strings.TrimSpace(req.TotpCode), time.Now()) {
		s.recordFailure(ctx, user.LoginName)
		return TokenResponse{}, NewAuthError(AuthErrorUnauthorized, "验证码错误或已过期")
	}

	s.clearFailures(ctx, user.LoginName)
	return s.issueTokenResponse(ctx, user, clientIP, userAgent)
}

func (s *AuthService) Refresh(ctx context.Context, refreshToken, clientIP, userAgent string) (TokenResponse, error) {
	refreshToken = strings.TrimSpace(refreshToken)
	if refreshToken == "" {
		return TokenResponse{}, NewAuthError(AuthErrorUnauthorized, "refreshToken无效或已过期")
	}
	if err := s.ensureReady(); err != nil {
		return TokenResponse{}, err
	}

	session, err := s.findActiveRefreshSession(ctx, refreshToken)
	if err != nil {
		return TokenResponse{}, err
	}
	if session == nil {
		previous, err := s.findPreviousRefreshSession(ctx, refreshToken)
		if err != nil {
			return TokenResponse{}, err
		}
		if previous != nil {
			if previous.PreviousTokenValidUntil.Valid && previous.PreviousTokenValidUntil.Time.After(time.Now()) {
				return TokenResponse{}, NewAuthError(AuthErrorConflict, "登录状态正在刷新，请稍后重试")
			}
			_ = s.revokeSession(ctx, previous.ID, "REUSE_DETECTED")
		}
		return TokenResponse{}, NewAuthError(AuthErrorUnauthorized, "refreshToken无效或已过期")
	}
	if session.RevokedAt.Valid || session.ExpiresAt.Before(time.Now()) {
		return TokenResponse{}, NewAuthError(AuthErrorUnauthorized, "refreshToken无效或已过期")
	}

	user, err := s.findUserByID(ctx, session.UserID)
	if err != nil {
		return TokenResponse{}, NewAuthError(AuthErrorUnauthorized, "用户不存在")
	}
	if user.Status != "NORMAL" {
		return TokenResponse{}, NewAuthError(AuthErrorForbidden, "账户已禁用")
	}
	return s.rotateSessionAndIssue(ctx, session, user, clientIP, userAgent)
}

func (s *AuthService) Logout(ctx context.Context, refreshToken string) error {
	refreshToken = strings.TrimSpace(refreshToken)
	if refreshToken == "" {
		return nil
	}
	if err := s.ensureReady(); err != nil {
		return err
	}
	session, err := s.findActiveRefreshSession(ctx, refreshToken)
	if err != nil || session == nil {
		return err
	}
	return s.revokeSession(ctx, session.ID, "MANUAL")
}

func (s *AuthService) AuthenticateAccessToken(ctx context.Context, rawToken string) (AuthenticatedPrincipal, error) {
	rawToken = strings.TrimSpace(rawToken)
	if rawToken == "" {
		return AuthenticatedPrincipal{}, NewAuthError(AuthErrorUnauthorized, "未登录")
	}
	if err := s.ensureReady(); err != nil {
		return AuthenticatedPrincipal{}, err
	}
	claims, err := s.parseAccessToken(ctx, rawToken)
	if err != nil {
		return AuthenticatedPrincipal{}, NewAuthError(AuthErrorUnauthorized, "登录状态已失效，请重新登录")
	}
	userID, err := claimInt64(claims, "uid")
	if err != nil || userID <= 0 {
		return AuthenticatedPrincipal{}, NewAuthError(AuthErrorUnauthorized, "登录状态已失效，请重新登录")
	}
	sessionID, _ := claimString(claims, "sid")
	issuedAt, err := claimTime(claims, "iat")
	if err != nil {
		return AuthenticatedPrincipal{}, NewAuthError(AuthErrorUnauthorized, "登录状态已失效，请重新登录")
	}
	if blacklisted, err := s.isAccessTokenBlacklisted(ctx, userID, sessionID, issuedAt); err != nil {
		return AuthenticatedPrincipal{}, err
	} else if blacklisted {
		return AuthenticatedPrincipal{}, NewAuthError(AuthErrorUnauthorized, "会话已失效，请重新登录")
	}
	user, err := s.findUserByID(ctx, userID)
	if err != nil || user.Status != "NORMAL" {
		return AuthenticatedPrincipal{}, NewAuthError(AuthErrorUnauthorized, "登录状态已失效，请重新登录")
	}
	s.touchSession(ctx, sessionID)
	return AuthenticatedPrincipal{
		UserID:         user.ID,
		LoginName:      user.LoginName,
		SessionID:      sessionID,
		IssuedAt:       issuedAt,
		TotpEnabled:    user.TotpEnabled,
		ForceTotpSetup: user.RequireTotpSetup,
	}, nil
}

func (s *AuthService) CurrentUserSecurityStatus(ctx context.Context, userID int64) (CurrentUserSecurityResponse, error) {
	if err := s.ensureReady(); err != nil {
		return CurrentUserSecurityResponse{}, err
	}
	user, err := s.findUserByID(ctx, userID)
	if err != nil {
		return CurrentUserSecurityResponse{}, NewAuthError(AuthErrorUnauthorized, "用户不存在")
	}
	forbidDisable2FA, err := s.isSwitchEnabled(ctx, forbidDisable2FASwitch)
	if err != nil {
		return CurrentUserSecurityResponse{}, err
	}
	return CurrentUserSecurityResponse{
		ID:               user.ID,
		LoginName:        user.LoginName,
		UserName:         user.UserName,
		TotpEnabled:      user.TotpEnabled,
		ForceTotpSetup:   user.RequireTotpSetup,
		ForbidDisable2FA: forbidDisable2FA,
	}, nil
}

func (s *AuthService) VerifyCurrentUserTOTP(ctx context.Context, userID int64, totpCode string) error {
	if err := s.ensureReady(); err != nil {
		return err
	}
	totpCode = strings.TrimSpace(totpCode)
	if !isSixDigitCode(totpCode) {
		return NewAuthError(AuthErrorValidation, "请提供6位2FA验证码")
	}
	user, err := s.findUserByID(ctx, userID)
	if err != nil {
		return NewAuthError(AuthErrorUnauthorized, "登录状态已失效，请重新登录")
	}
	if !user.TotpEnabled || !user.TotpSecret.Valid || strings.TrimSpace(user.TotpSecret.String) == "" {
		return NewAuthError(AuthErrorBusiness, "当前账号未启用2FA，无法执行该敏感操作")
	}
	secret, err := s.decryptTotpSecret(ctx, user.TotpSecret.String)
	if err != nil {
		return NewAuthError(AuthErrorBusiness, "当前账号2FA密钥不可用，请重新生成后再试")
	}
	if !verifyTOTP(secret, totpCode, time.Now()) {
		return NewAuthError(AuthErrorBusiness, "2FA验证码错误或已过期")
	}
	return nil
}

func (s *AuthService) ChangePassword(ctx context.Context, userID int64, currentPassword, newPassword string) error {
	if err := s.ensureReady(); err != nil {
		return err
	}
	currentPassword = strings.TrimSpace(currentPassword)
	newPassword = strings.TrimSpace(newPassword)
	if currentPassword == "" || newPassword == "" {
		return NewAuthError(AuthErrorValidation, "密码不能为空")
	}
	if currentPassword == newPassword {
		return NewAuthError(AuthErrorValidation, "新密码不能与当前密码相同")
	}
	user, err := s.findUserByID(ctx, userID)
	if err != nil {
		return NewAuthError(AuthErrorNotFound, "用户不存在")
	}
	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(currentPassword)); err != nil {
		return NewAuthError(AuthErrorBusiness, "当前密码错误")
	}
	hashed, err := bcrypt.GenerateFromPassword([]byte(newPassword), bcrypt.DefaultCost)
	if err != nil {
		return err
	}
	_, err = s.db.Exec(ctx, `
		UPDATE sys_user
		   SET password_hash = $1,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $2 AND deleted_flag = false
	`, string(hashed), userID)
	if err != nil {
		return err
	}
	return nil
}

func (s *AuthService) ChangeCurrentUserPassword(ctx context.Context, userID int64, currentPassword, newPassword string) error {
	return s.ChangePassword(ctx, userID, currentPassword, newPassword)
}

func (s *AuthService) SetupTotp(ctx context.Context, userID int64) (TotpSetupResponse, error) {
	if err := s.ensureReady(); err != nil {
		return TotpSetupResponse{}, err
	}
	user, err := s.findUserByID(ctx, userID)
	if err != nil {
		return TotpSetupResponse{}, NewAuthError(AuthErrorNotFound, "用户不存在")
	}
	if user.TotpEnabled {
		return TotpSetupResponse{}, NewAuthError(AuthErrorBusiness, "2FA 已启用，请先关闭 2FA 后再重新绑定")
	}
	plainSecret, err := generateTotpSecret()
	if err != nil {
		return TotpSetupResponse{}, err
	}
	encryptedSecret, err := s.encryptTotpSecret(ctx, plainSecret)
	if err != nil {
		return TotpSetupResponse{}, err
	}
	_, err = s.db.Exec(ctx, `
		UPDATE sys_user
		   SET totp_secret = $1,
		       totp_enabled = false,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $2 AND deleted_flag = false
	`, encryptedSecret, userID)
	if err != nil {
		return TotpSetupResponse{}, err
	}
	qrCodeBase64, err := generateTotpQrCodeBase64(plainSecret, user.LoginName, s.totpIssuer())
	if err != nil {
		return TotpSetupResponse{}, err
	}
	return TotpSetupResponse{QrCodeBase64: qrCodeBase64, Secret: plainSecret}, nil
}

func (s *AuthService) SetupCurrentUser2FA(ctx context.Context, userID int64) (string, string, error) {
	result, err := s.SetupTotp(ctx, userID)
	if err != nil {
		return "", "", err
	}
	return result.QrCodeBase64, result.Secret, nil
}

func (s *AuthService) EnableTotp(ctx context.Context, userID int64, code string) (CurrentUserSecurityResponse, error) {
	if err := s.ensureReady(); err != nil {
		return CurrentUserSecurityResponse{}, err
	}
	code = strings.TrimSpace(code)
	if code == "" {
		return CurrentUserSecurityResponse{}, NewAuthError(AuthErrorValidation, "totpCode: 验证码不能为空")
	}
	if !isSixDigitCode(code) {
		return CurrentUserSecurityResponse{}, NewAuthError(AuthErrorValidation, "totpCode: 验证码必须为6位数字")
	}
	user, err := s.findUserByID(ctx, userID)
	if err != nil {
		return CurrentUserSecurityResponse{}, NewAuthError(AuthErrorNotFound, "用户不存在")
	}
	if strings.TrimSpace(user.TotpSecret.String) == "" {
		return CurrentUserSecurityResponse{}, NewAuthError(AuthErrorBusiness, "请先生成2FA密钥")
	}
	secret, err := s.decryptTotpSecret(ctx, user.TotpSecret.String)
	if err != nil {
		return CurrentUserSecurityResponse{}, err
	}
	if !verifyTOTP(secret, code, time.Now()) {
		return CurrentUserSecurityResponse{}, NewAuthError(AuthErrorBusiness, "验证码错误或已过期")
	}
	_, err = s.db.Exec(ctx, `
		UPDATE sys_user
		   SET totp_enabled = true,
		       require_totp_setup = false,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1 AND deleted_flag = false
	`, userID)
	if err != nil {
		return CurrentUserSecurityResponse{}, err
	}
	return s.CurrentUserSecurityStatus(ctx, userID)
}

func (s *AuthService) EnableCurrentUser2FA(ctx context.Context, userID int64, totpCode string) (CurrentUserSecurityResponse, error) {
	return s.EnableTotp(ctx, userID, totpCode)
}

func (s *AuthService) DisableTotp(ctx context.Context, userID int64) (CurrentUserSecurityResponse, error) {
	if err := s.ensureReady(); err != nil {
		return CurrentUserSecurityResponse{}, err
	}
	forbidDisable2FA, err := s.isSwitchEnabled(ctx, forbidDisable2FASwitch)
	if err != nil {
		return CurrentUserSecurityResponse{}, err
	}
	if forbidDisable2FA {
		return CurrentUserSecurityResponse{}, NewAuthError(AuthErrorBusiness, "系统设置禁止关闭 2FA，请联系管理员")
	}
	if _, err := s.findUserByID(ctx, userID); err != nil {
		return CurrentUserSecurityResponse{}, NewAuthError(AuthErrorNotFound, "用户不存在")
	}
	_, err = s.db.Exec(ctx, `
		UPDATE sys_user
		   SET totp_secret = NULL,
		       totp_enabled = false,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1 AND deleted_flag = false
	`, userID)
	if err != nil {
		return CurrentUserSecurityResponse{}, err
	}
	return s.CurrentUserSecurityStatus(ctx, userID)
}

func (s *AuthService) DisableCurrentUser2FA(ctx context.Context, userID int64, totpCode string) (CurrentUserSecurityResponse, error) {
	totpCode = strings.TrimSpace(totpCode)
	if !isSixDigitCode(totpCode) {
		return CurrentUserSecurityResponse{}, NewAuthError(AuthErrorValidation, "请提供6位2FA验证码")
	}
	if err := s.ensureReady(); err != nil {
		return CurrentUserSecurityResponse{}, err
	}
	user, err := s.findUserByID(ctx, userID)
	if err != nil {
		return CurrentUserSecurityResponse{}, NewAuthError(AuthErrorNotFound, "用户不存在")
	}
	if !user.TotpEnabled || strings.TrimSpace(user.TotpSecret.String) == "" {
		return CurrentUserSecurityResponse{}, NewAuthError(AuthErrorBusiness, "当前账号未启用2FA，无法执行该敏感操作")
	}
	secret, err := s.decryptTotpSecret(ctx, user.TotpSecret.String)
	if err != nil {
		return CurrentUserSecurityResponse{}, NewAuthError(AuthErrorBusiness, "当前账号2FA密钥不可用，请重新生成后再试")
	}
	if !verifyTOTP(secret, totpCode, time.Now()) {
		return CurrentUserSecurityResponse{}, NewAuthError(AuthErrorBusiness, "2FA验证码错误或已过期")
	}
	return s.DisableTotp(ctx, userID)
}

func (s *AuthService) verifyCaptcha(ctx context.Context, req LoginRequest, captcha CaptchaVerifier) error {
	if captcha == nil {
		return nil
	}
	required, err := captcha.ShouldRequireLoginCaptcha(ctx)
	if err != nil {
		return err
	}
	if !required {
		return nil
	}
	ok, err := captcha.Verify(ctx, req.CaptchaID, req.CaptchaCode)
	if err != nil {
		return err
	}
	if !ok {
		return NewAuthError(AuthErrorUnauthorized, "图形验证码错误或已过期")
	}
	return nil
}

func (s *AuthService) ensureReady() error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	if s.redis == nil {
		return errors.New("redis client is not configured")
	}
	return nil
}

func (s *AuthService) issueTokenResponse(ctx context.Context, user authUserRecord, clientIP, userAgent string) (TokenResponse, error) {
	sessionID := newUUID()
	accessToken, err := s.signAccessToken(ctx, user.LoginName, user.ID, sessionID)
	if err != nil {
		return TokenResponse{}, err
	}
	refreshToken, err := randomRefreshToken()
	if err != nil {
		return TokenResponse{}, err
	}
	if err := s.createRefreshSession(ctx, user.ID, sessionID, refreshToken, clientIP, userAgent); err != nil {
		return TokenResponse{}, err
	}
	_ = s.updateLastLoginDate(ctx, user.ID)

	authUser, err := s.buildAuthUser(ctx, user)
	if err != nil {
		return TokenResponse{}, err
	}
	return TokenResponse{
		AccessToken:      accessToken,
		RefreshToken:     refreshToken,
		TokenType:        tokenTypeBearer,
		ExpiresIn:        int64(s.accessTTL / time.Second),
		RefreshExpiresIn: int64(s.refreshTTL / time.Second),
		User:             authUser,
	}, nil
}

func (s *AuthService) rotateSessionAndIssue(ctx context.Context, session *refreshSessionRecord, user authUserRecord, clientIP, userAgent string) (TokenResponse, error) {
	nextRefreshToken, err := randomRefreshToken()
	if err != nil {
		return TokenResponse{}, err
	}
	nextHash := hashToken(nextRefreshToken)
	now := time.Now()
	_, err = s.db.Exec(ctx, `
		UPDATE auth_refresh_token
		   SET token_hash = $1,
		       previous_token_hash = $2,
		       previous_token_valid_until = $3,
		       expires_at = $4,
		       login_ip = $5,
		       device_info = $6,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $7
	`, nextHash, sql.NullString{String: session.TokenHash, Valid: true}, sql.NullTime{Time: now.Add(refreshReuseGrace), Valid: true}, now.Add(s.refreshTTL), nullString(clientIP), nullString(userAgent), session.ID)
	if err != nil {
		return TokenResponse{}, err
	}
	accessToken, err := s.signAccessToken(ctx, user.LoginName, user.ID, session.TokenID)
	if err != nil {
		return TokenResponse{}, err
	}
	authUser, err := s.buildAuthUser(ctx, user)
	if err != nil {
		return TokenResponse{}, err
	}
	return TokenResponse{
		AccessToken:      accessToken,
		RefreshToken:     nextRefreshToken,
		TokenType:        tokenTypeBearer,
		ExpiresIn:        int64(s.accessTTL / time.Second),
		RefreshExpiresIn: int64(s.refreshTTL / time.Second),
		User:             authUser,
	}, nil
}

func (s *AuthService) buildAuthUser(ctx context.Context, user authUserRecord) (AuthUserResponse, error) {
	roleName, permissions, dataScopes, err := s.loadPrincipalData(ctx, user.ID)
	if err != nil {
		return AuthUserResponse{}, err
	}
	return AuthUserResponse{
		ID:             user.ID,
		LoginName:      user.LoginName,
		UserName:       user.UserName,
		RoleName:       roleName,
		TotpEnabled:    user.TotpEnabled,
		ForceTotpSetup: user.RequireTotpSetup,
		Permissions:    permissions,
		DataScopes:     dataScopes,
	}, nil
}

func (s *AuthService) findUserByLoginName(ctx context.Context, loginName string) (authUserRecord, error) {
	var user authUserRecord
	err := s.db.QueryRow(ctx, `
		SELECT id, login_name, password_hash, user_name,
		       totp_secret, totp_enabled, require_totp_setup, status
		  FROM sys_user
		 WHERE login_name = $1 AND deleted_flag = false
		 LIMIT 1
	`, loginName).Scan(
		&user.ID,
		&user.LoginName,
		&user.PasswordHash,
		&user.UserName,
		&user.TotpSecret,
		&user.TotpEnabled,
		&user.RequireTotpSetup,
		&user.Status,
	)
	return user, err
}

func (s *AuthService) findUserByID(ctx context.Context, id int64) (authUserRecord, error) {
	var user authUserRecord
	err := s.db.QueryRow(ctx, `
		SELECT id, login_name, password_hash, user_name,
		       totp_secret, totp_enabled, require_totp_setup, status
		  FROM sys_user
		 WHERE id = $1 AND deleted_flag = false
		 LIMIT 1
	`, id).Scan(
		&user.ID,
		&user.LoginName,
		&user.PasswordHash,
		&user.UserName,
		&user.TotpSecret,
		&user.TotpEnabled,
		&user.RequireTotpSetup,
		&user.Status,
	)
	return user, err
}

func (s *AuthService) loadPrincipalData(ctx context.Context, userID int64) (string, []ResourcePermissionResponse, map[string]string, error) {
	roleRows, err := s.db.Query(ctx, `
		SELECT DISTINCT role.role_name
		  FROM sys_user_role user_role
		  JOIN sys_role role ON role.id = user_role.role_id
		 WHERE user_role.user_id = $1
		   AND user_role.deleted_flag = false
		   AND role.deleted_flag = false
		   AND role.status = '正常'
		 ORDER BY role.role_name
	`, userID)
	if err != nil {
		return "", nil, nil, err
	}
	defer roleRows.Close()

	var roleNames []string
	for roleRows.Next() {
		var roleName string
		if err := roleRows.Scan(&roleName); err != nil {
			return "", nil, nil, err
		}
		roleName = strings.TrimSpace(roleName)
		if roleName != "" {
			roleNames = append(roleNames, roleName)
		}
	}
	if err := roleRows.Err(); err != nil {
		return "", nil, nil, err
	}

	permissionRows, err := s.db.Query(ctx, `
		SELECT rp.resource_code, rp.action_code, COALESCE(role.data_scope, 'self')
		  FROM sys_user_role user_role
		  JOIN sys_role role ON role.id = user_role.role_id
		  JOIN sys_role_permission rp ON rp.role_id = role.id
		 WHERE user_role.user_id = $1
		   AND user_role.deleted_flag = false
		   AND role.deleted_flag = false
		   AND role.status = '正常'
		   AND rp.deleted_flag = false
	`, userID)
	if err != nil {
		return "", nil, nil, err
	}
	defer permissionRows.Close()

	actionSetByResource := map[string]map[string]struct{}{}
	dataScopes := map[string]string{}
	for permissionRows.Next() {
		var resource, action, scope string
		if err := permissionRows.Scan(&resource, &action, &scope); err != nil {
			return "", nil, nil, err
		}
		resource = normalizeResourceCode(resource)
		action = normalizeActionCode(action)
		if resource == "" || action == "" {
			continue
		}
		if _, ok := actionSetByResource[resource]; !ok {
			actionSetByResource[resource] = map[string]struct{}{}
		}
		actionSetByResource[resource][action] = struct{}{}
		dataScopes[resource] = broaderDataScope(dataScopes[resource], scope)
	}
	if err := permissionRows.Err(); err != nil {
		return "", nil, nil, err
	}

	permissions := make([]ResourcePermissionResponse, 0, len(actionSetByResource))
	for resource, actionSet := range actionSetByResource {
		actions := make([]string, 0, len(actionSet))
		for action := range actionSet {
			actions = append(actions, action)
		}
		sort.Strings(actions)
		permissions = append(permissions, ResourcePermissionResponse{Resource: resource, Actions: actions})
	}
	sort.Slice(permissions, func(i, j int) bool {
		return permissions[i].Resource < permissions[j].Resource
	})
	return strings.Join(roleNames, ","), permissions, dataScopes, nil
}

func (s *AuthService) issueTwoFactorTempToken(ctx context.Context, userID int64) (string, error) {
	tempToken := fmt.Sprintf("%d.%s", userID, newUUID())
	userKey := auth2FAUserTempPrefix + strconv.FormatInt(userID, 10)
	if previous, err := s.redis.Get(ctx, userKey).Result(); err == nil && strings.TrimSpace(previous) != "" {
		_ = s.redis.Del(ctx, auth2FATempPrefix+previous).Err()
	}
	if err := s.redis.Set(ctx, auth2FATempPrefix+tempToken, strconv.FormatInt(userID, 10), auth2FATempTTL).Err(); err != nil {
		return "", err
	}
	if err := s.redis.Set(ctx, userKey, tempToken, auth2FATempTTL).Err(); err != nil {
		return "", err
	}
	return tempToken, nil
}

func (s *AuthService) createRefreshSession(ctx context.Context, userID int64, tokenID, refreshToken, clientIP, userAgent string) error {
	if err := s.trimActiveRefreshSessions(ctx, userID); err != nil {
		return err
	}
	_, err := s.db.Exec(ctx, `
		INSERT INTO auth_refresh_token (
			id, user_id, token_id, token_hash, expires_at, login_ip, device_info,
			created_by, created_name, deleted_flag
		) VALUES ($1, $2, $3, $4, $5, $6, $7, 0, 'system', false)
	`, s.idGenerator.Next(), userID, tokenID, hashToken(refreshToken), time.Now().Add(s.refreshTTL), nullString(clientIP), nullString(userAgent))
	return err
}

func (s *AuthService) trimActiveRefreshSessions(ctx context.Context, userID int64) error {
	maxSessions, err := s.maxConcurrentSessions(ctx)
	if err != nil {
		return err
	}
	if maxSessions <= 0 {
		return nil
	}
	limitBeforeCreate := maxSessions - 1
	_, err = s.db.Exec(ctx, `
		WITH active AS (
			SELECT id
			  FROM auth_refresh_token
			 WHERE user_id = $1
			   AND deleted_flag = false
			   AND revoked_at IS NULL
			   AND expires_at > CURRENT_TIMESTAMP
			 ORDER BY created_at ASC
		),
		to_revoke AS (
			SELECT id FROM active OFFSET $2
		)
		UPDATE auth_refresh_token
		   SET revoked_at = CURRENT_TIMESTAMP,
		       revoke_reason = 'CONCURRENT_LIMIT',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id IN (SELECT id FROM to_revoke)
	`, userID, limitBeforeCreate)
	return err
}

func (s *AuthService) maxConcurrentSessions(ctx context.Context) (int, error) {
	var sample string
	err := s.db.QueryRow(ctx, `
		SELECT sample_no
		  FROM sys_no_rule
		 WHERE setting_code = 'SYS_MAX_CONCURRENT_SESSIONS'
		   AND status = '正常'
		   AND deleted_flag = false
		 LIMIT 1
	`).Scan(&sample)
	if errors.Is(err, pgx.ErrNoRows) {
		return 3, nil
	}
	if err != nil {
		return 0, err
	}
	value, err := strconv.Atoi(strings.TrimSpace(sample))
	if err != nil || value < 0 {
		return 3, nil
	}
	return value, nil
}

func (s *AuthService) findActiveRefreshSession(ctx context.Context, refreshToken string) (*refreshSessionRecord, error) {
	return s.findRefreshSessionByHash(ctx, "token_hash", hashToken(refreshToken))
}

func (s *AuthService) findPreviousRefreshSession(ctx context.Context, refreshToken string) (*refreshSessionRecord, error) {
	return s.findRefreshSessionByHash(ctx, "previous_token_hash", hashToken(refreshToken))
}

func (s *AuthService) findRefreshSessionByHash(ctx context.Context, field string, tokenHash string) (*refreshSessionRecord, error) {
	query := fmt.Sprintf(`
		SELECT id, user_id, token_id, token_hash, previous_token_hash, previous_token_valid_until,
		       expires_at, revoked_at, revoke_reason
		  FROM auth_refresh_token
		 WHERE %s = $1 AND deleted_flag = false
		 LIMIT 1
	`, field)
	var session refreshSessionRecord
	err := s.db.QueryRow(ctx, query, tokenHash).Scan(
		&session.ID,
		&session.UserID,
		&session.TokenID,
		&session.TokenHash,
		&session.PreviousTokenHash,
		&session.PreviousTokenValidUntil,
		&session.ExpiresAt,
		&session.RevokedAt,
		&session.RevokeReason,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &session, nil
}

func (s *AuthService) revokeSession(ctx context.Context, sessionID int64, reason string) error {
	_, err := s.db.Exec(ctx, `
		UPDATE auth_refresh_token
		   SET revoked_at = CURRENT_TIMESTAMP,
		       revoke_reason = $1,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $2
	`, reason, sessionID)
	return err
}

func (s *AuthService) updateLastLoginDate(ctx context.Context, userID int64) error {
	_, err := s.db.Exec(ctx, `
		UPDATE sys_user
		   SET last_login_date = CURRENT_TIMESTAMP,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
	`, userID)
	return err
}

func (s *AuthService) signAccessToken(ctx context.Context, loginName string, userID int64, sessionID string) (string, error) {
	material, err := s.resolveActiveSecretMaterial(ctx, jwtSecretType, s.jwtSecret, "LEO_JWT_SECRET")
	if err != nil {
		return "", err
	}
	now := time.Now()
	claims := jwt.MapClaims{
		"iss": s.issuer,
		"sub": loginName,
		"uid": userID,
		"sid": sessionID,
		"iat": now.Unix(),
		"exp": now.Add(s.accessTTL).Unix(),
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	token.Header["kid"] = strconv.Itoa(material.Version)
	return token.SignedString([]byte(material.Value))
}

func (s *AuthService) parseAccessToken(ctx context.Context, rawToken string) (jwt.MapClaims, error) {
	materials, err := s.resolveJwtVerificationMaterials(ctx)
	if err != nil {
		return nil, err
	}
	var lastErr error
	for _, material := range materials {
		claims := jwt.MapClaims{}
		parsed, err := jwt.ParseWithClaims(rawToken, claims, func(token *jwt.Token) (any, error) {
			if token.Method != jwt.SigningMethodHS256 {
				return nil, fmt.Errorf("unexpected jwt signing method: %s", token.Method.Alg())
			}
			return []byte(material.Value), nil
		}, jwt.WithIssuer(s.issuer), jwt.WithExpirationRequired(), jwt.WithIssuedAt())
		if err != nil || parsed == nil || !parsed.Valid {
			if err != nil {
				lastErr = err
			}
			continue
		}
		if err := validateJwtKeyWindow(material, claims, s.accessTTL); err != nil {
			lastErr = err
			continue
		}
		return claims, nil
	}
	if lastErr != nil {
		return nil, lastErr
	}
	return nil, NewAuthError(AuthErrorInternal, "未找到可用的 JWT 验签密钥")
}

func (s *AuthService) resolveJwtVerificationMaterials(ctx context.Context) ([]secretMaterial, error) {
	if s.db != nil {
		rows, err := s.db.Query(ctx, `
			SELECT key_version, secret_value, retired_at
			  FROM sys_security_secret
			 WHERE secret_type = $1
			   AND status IN ('ACTIVE', 'RETIRED')
			   AND deleted_flag = false
			 ORDER BY key_version DESC
		`, jwtSecretType)
		if err != nil {
			return nil, err
		}
		defer rows.Close()

		now := time.Now()
		var materials []secretMaterial
		for rows.Next() {
			var material secretMaterial
			if err := rows.Scan(&material.Version, &material.Value, &material.RetiredAt); err != nil {
				return nil, err
			}
			material.Source = secretSourceDB
			material.Value = strings.TrimSpace(material.Value)
			if material.Value == "" || !jwtVerificationMaterialUsable(material, now, s.accessTTL) {
				continue
			}
			materials = append(materials, material)
		}
		if err := rows.Err(); err != nil {
			return nil, err
		}
		if len(materials) > 0 {
			return materials, nil
		}
	}
	material, err := s.resolveActiveSecretMaterial(ctx, jwtSecretType, s.jwtSecret, "LEO_JWT_SECRET")
	if err != nil {
		return nil, err
	}
	return []secretMaterial{material}, nil
}

func validateJwtKeyWindow(material secretMaterial, claims jwt.MapClaims, accessTTL time.Duration) error {
	if !material.RetiredAt.Valid {
		return nil
	}
	issuedAt, err := claimTime(claims, "iat")
	if err != nil {
		return fmt.Errorf("JWT 缺少签发时间")
	}
	if issuedAt.After(material.RetiredAt.Time) {
		return fmt.Errorf("JWT 使用了已退役的签名密钥")
	}
	expiresAt, err := claimTime(claims, "exp")
	if err != nil {
		return fmt.Errorf("JWT 缺少过期时间")
	}
	if expiresAt.After(material.RetiredAt.Time.Add(accessTTL)) {
		return fmt.Errorf("JWT 超出了历史密钥允许的有效窗口")
	}
	return nil
}

func jwtVerificationMaterialUsable(material secretMaterial, now time.Time, accessTTL time.Duration) bool {
	if !material.RetiredAt.Valid {
		return true
	}
	return !material.RetiredAt.Time.Add(accessTTL).Before(now)
}

func (s *AuthService) decryptTotpSecret(ctx context.Context, encryptedSecret string) (string, error) {
	material, err := s.resolveActiveSecretMaterial(ctx, totpSecretType, os.Getenv("TOTP_ENCRYPTION_KEY"), "TOTP_ENCRYPTION_KEY")
	if err != nil {
		return "", err
	}
	raw, err := base64.StdEncoding.DecodeString(strings.TrimSpace(encryptedSecret))
	if err != nil {
		return "", NewAuthError(AuthErrorUnauthorized, "验证码错误或已过期")
	}
	if len(raw) < 13 {
		return "", NewAuthError(AuthErrorUnauthorized, "验证码错误或已过期")
	}
	block, err := aes.NewCipher(deriveAESKey(material.Value))
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	plain, err := gcm.Open(nil, raw[:gcm.NonceSize()], raw[gcm.NonceSize():], nil)
	if err != nil {
		return "", NewAuthError(AuthErrorUnauthorized, "验证码错误或已过期")
	}
	return string(plain), nil
}

func (s *AuthService) encryptTotpSecret(ctx context.Context, plainSecret string) (string, error) {
	material, err := s.resolveActiveSecretMaterial(ctx, totpSecretType, os.Getenv("TOTP_ENCRYPTION_KEY"), "TOTP_ENCRYPTION_KEY")
	if err != nil {
		return "", err
	}
	block, err := aes.NewCipher(deriveAESKey(material.Value))
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return "", err
	}
	ciphertext := gcm.Seal(nil, nonce, []byte(strings.TrimSpace(plainSecret)), nil)
	return base64.StdEncoding.EncodeToString(append(nonce, ciphertext...)), nil
}

func generateTotpSecret() (string, error) {
	raw := make([]byte, totpSecretLength)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	var builder strings.Builder
	builder.Grow(totpSecretLength)
	for _, value := range raw {
		builder.WriteByte(totpSecretAlphabet[int(value)%len(totpSecretAlphabet)])
	}
	return builder.String(), nil
}

func generateTotpQrCodeBase64(secret, loginName, issuer string) (string, error) {
	if strings.TrimSpace(issuer) == "" {
		issuer = defaultTotpIssuer
	}
	payload := fmt.Sprintf("otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
		urlQueryEscape(strings.TrimSpace(loginName)),
		urlQueryEscape(strings.TrimSpace(secret)),
		urlQueryEscape(strings.TrimSpace(issuer)),
	)
	pngBytes, err := qrcode.Encode(payload, qrcode.Medium, defaultQrCodeSize)
	if err != nil {
		return "", err
	}
	return base64.StdEncoding.EncodeToString(pngBytes), nil
}

func (s *AuthService) totpIssuer() string {
	if v := strings.TrimSpace(os.Getenv("LEO_SECURITY_TOTP_ISSUER")); v != "" {
		return v
	}
	if v := strings.TrimSpace(os.Getenv("TOTP_ISSUER")); v != "" {
		return v
	}
	return defaultTotpIssuer
}

func urlQueryEscape(value string) string {
	return strings.ReplaceAll(url.QueryEscape(value), "+", "%20")
}

func (s *AuthService) resolveActiveSecretMaterial(ctx context.Context, secretType string, fallback string, fallbackName string) (secretMaterial, error) {
	var secret string
	var version int
	if s.db != nil {
		err := s.db.QueryRow(ctx, `
			SELECT key_version, secret_value
			  FROM sys_security_secret
			 WHERE secret_type = $1
			   AND status = 'ACTIVE'
			   AND deleted_flag = false
			 ORDER BY key_version DESC
			 LIMIT 1
		`, secretType).Scan(&version, &secret)
		if err == nil && strings.TrimSpace(secret) != "" {
			return secretMaterial{Source: secretSourceDB, Version: version, Value: strings.TrimSpace(secret)}, nil
		}
		if err != nil && !errors.Is(err, pgx.ErrNoRows) {
			return secretMaterial{}, err
		}
	}
	fallback = strings.TrimSpace(fallback)
	if fallback == "" {
		return secretMaterial{}, NewAuthError(AuthErrorInternal, missingSecretMessage(secretType, fallbackName))
	}
	if secretType == jwtSecretType && len(fallback) < 32 {
		return secretMaterial{}, NewAuthError(AuthErrorInternal, "启动兜底环境变量 LEO_JWT_SECRET 长度不能少于 32 位")
	}
	return secretMaterial{Source: secretSourceConfig, Version: 0, Value: fallback}, nil
}

func missingSecretMessage(secretType string, fallbackName string) string {
	secretName := "安全主密钥"
	if secretType == jwtSecretType {
		secretName = "JWT 主密钥"
	}
	if secretType == totpSecretType {
		secretName = "2FA 主密钥"
	}
	return "数据库中未找到可用的" + secretName + "，且启动兜底环境变量 " + fallbackName + " 未配置"
}

func (s *AuthService) isAccessTokenBlacklisted(ctx context.Context, userID int64, sessionID string, issuedAt time.Time) (bool, error) {
	if strings.TrimSpace(sessionID) != "" {
		exists, err := s.redis.Exists(ctx, accessTokenSessionBlacklistPrefix+strings.TrimSpace(sessionID)).Result()
		if err != nil {
			return false, err
		}
		if exists > 0 {
			return true, nil
		}
	}
	value, err := s.redis.Get(ctx, accessTokenUserBlacklistPrefix+strconv.FormatInt(userID, 10)).Result()
	if errors.Is(err, redis.Nil) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	blacklistedAt, err := strconv.ParseInt(strings.TrimSpace(value), 10, 64)
	if err != nil {
		return true, nil
	}
	return issuedAt.UnixMilli() < blacklistedAt, nil
}

func (s *AuthService) touchSession(ctx context.Context, sessionID string) {
	sessionID = strings.TrimSpace(sessionID)
	if sessionID == "" || s.redis == nil {
		return
	}
	_ = s.redis.Set(ctx, sessionActivityPrefix+sessionID, strconv.FormatInt(time.Now().UnixMilli(), 10), sessionActivityTTL).Err()
}

func (s *AuthService) isSwitchEnabled(ctx context.Context, settingCode string) (bool, error) {
	var enabled bool
	err := s.db.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1
			  FROM sys_no_rule
			 WHERE setting_code = $1
			   AND status = '正常'
			   AND deleted_flag = false
		)
	`, settingCode).Scan(&enabled)
	return enabled, err
}

func (s *AuthService) ensureLoginAllowed(ctx context.Context, loginName string) error {
	lockKey := authLoginLockPrefix + normalizeLoginName(loginName)
	exists, err := s.redis.Exists(ctx, lockKey).Result()
	if err != nil {
		return err
	}
	if exists == 0 {
		return nil
	}
	ttl, err := s.redis.TTL(ctx, lockKey).Result()
	if err != nil {
		return err
	}
	if ttl < time.Second {
		ttl = time.Second
	}
	return NewAuthError(AuthErrorForbidden, "登录失败次数过多，请在 "+formatWaitTime(ttl)+" 后重试")
}

func (s *AuthService) recordFailure(ctx context.Context, loginName string) {
	key := authLoginFailurePrefix + normalizeLoginName(loginName)
	count, err := s.redis.Incr(ctx, key).Result()
	if err != nil {
		return
	}
	if count == 1 {
		_ = s.redis.Expire(ctx, key, loginFailureWindow).Err()
	}
	if count >= loginMaxFailures {
		_ = s.redis.Set(ctx, authLoginLockPrefix+normalizeLoginName(loginName), time.Now().UnixMilli(), loginLockDuration).Err()
		_ = s.redis.Del(ctx, key).Err()
	}
}

func (s *AuthService) clearFailures(ctx context.Context, loginName string) {
	_ = s.redis.Del(ctx, authLoginFailurePrefix+normalizeLoginName(loginName)).Err()
	_ = s.redis.Del(ctx, authLoginLockPrefix+normalizeLoginName(loginName)).Err()
}

func loginBodyFromToken(token TokenResponse) LoginResponseBody {
	refreshToken := token.RefreshToken
	return LoginResponseBody{
		AccessToken:      token.AccessToken,
		RefreshToken:     &refreshToken,
		TokenType:        token.TokenType,
		ExpiresIn:        token.ExpiresIn,
		RefreshExpiresIn: token.RefreshExpiresIn,
		User:             &token.User,
	}
}

func hashToken(rawToken string) string {
	sum := sha256.Sum256([]byte(rawToken))
	return hex.EncodeToString(sum[:])
}

func claimString(claims jwt.MapClaims, key string) (string, error) {
	value, ok := claims[key]
	if !ok || value == nil {
		return "", fmt.Errorf("claim %s is missing", key)
	}
	text := strings.TrimSpace(fmt.Sprint(value))
	if text == "" {
		return "", fmt.Errorf("claim %s is blank", key)
	}
	return text, nil
}

func claimInt64(claims jwt.MapClaims, key string) (int64, error) {
	value, ok := claims[key]
	if !ok || value == nil {
		return 0, fmt.Errorf("claim %s is missing", key)
	}
	switch typed := value.(type) {
	case float64:
		return int64(typed), nil
	case int64:
		return typed, nil
	case int:
		return int64(typed), nil
	case string:
		return strconv.ParseInt(strings.TrimSpace(typed), 10, 64)
	default:
		return strconv.ParseInt(strings.TrimSpace(fmt.Sprint(value)), 10, 64)
	}
}

func claimTime(claims jwt.MapClaims, key string) (time.Time, error) {
	value, err := claimInt64(claims, key)
	if err != nil {
		return time.Time{}, err
	}
	return time.Unix(value, 0), nil
}

func randomRefreshToken() (string, error) {
	raw := make([]byte, 64)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(raw), nil
}

func newUUID() string {
	var raw [16]byte
	if _, err := rand.Read(raw[:]); err != nil {
		return strconv.FormatInt(time.Now().UnixNano(), 10)
	}
	raw[6] = (raw[6] & 0x0f) | 0x40
	raw[8] = (raw[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", raw[0:4], raw[4:6], raw[6:8], raw[8:10], raw[10:16])
}

func nullString(value string) sql.NullString {
	value = strings.TrimSpace(value)
	if value == "" {
		return sql.NullString{}
	}
	return sql.NullString{String: value, Valid: true}
}

func normalizeLoginName(value string) string {
	return strings.ToLower(strings.TrimSpace(value))
}

func normalizeResourceCode(value string) string {
	return strings.Trim(strings.ToLower(strings.TrimSpace(value)), "/")
}

func normalizeActionCode(value string) string {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "view":
		return "read"
	case "edit":
		return "update"
	default:
		return strings.ToLower(strings.TrimSpace(value))
	}
}

func normalizeDataScope(value string) string {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "全部数据", "全部", "all":
		return "all"
	case "本部门", "department":
		return "department"
	case "自定义范围", "custom":
		return "custom"
	default:
		return "self"
	}
}

func broaderDataScope(left, right string) string {
	left = normalizeDataScope(left)
	right = normalizeDataScope(right)
	if scopeRank(left) >= scopeRank(right) {
		return left
	}
	return right
}

func scopeRank(scope string) int {
	switch normalizeDataScope(scope) {
	case "all":
		return 4
	case "department":
		return 2
	default:
		return 1
	}
}

func formatWaitTime(d time.Duration) string {
	seconds := int64(d.Seconds())
	if seconds < 60 {
		return strconv.FormatInt(maxInt64(seconds, 1), 10) + " 秒"
	}
	minutes := seconds / 60
	remain := seconds % 60
	if remain == 0 {
		return strconv.FormatInt(minutes, 10) + " 分钟"
	}
	return fmt.Sprintf("%d 分钟 %d 秒", minutes, remain)
}

func maxInt64(left, right int64) int64 {
	if left > right {
		return left
	}
	return right
}

func deriveAESKey(secret string) []byte {
	key := make([]byte, 32)
	copy(key, []byte(secret))
	return key
}

func verifyTOTP(secret string, code string, now time.Time) bool {
	if len(code) != 6 {
		return false
	}
	normalizedSecret := strings.ToUpper(strings.ReplaceAll(strings.TrimSpace(secret), " ", ""))
	decoded, err := base32.StdEncoding.WithPadding(base32.NoPadding).DecodeString(normalizedSecret)
	if err != nil {
		if padded := normalizedSecret + strings.Repeat("=", (8-len(normalizedSecret)%8)%8); padded != normalizedSecret {
			decoded, err = base32.StdEncoding.DecodeString(padded)
		}
	}
	if err != nil {
		return false
	}
	step := now.Unix() / totpPeriodSeconds
	for _, candidateStep := range []int64{step - 1, step, step + 1} {
		if totpCode(decoded, candidateStep) == code {
			return true
		}
	}
	return false
}

func isSixDigitCode(code string) bool {
	if len(code) != 6 {
		return false
	}
	for _, char := range code {
		if char < '0' || char > '9' {
			return false
		}
	}
	return true
}

func totpCode(secret []byte, step int64) string {
	var counter [8]byte
	binary.BigEndian.PutUint64(counter[:], uint64(step))
	mac := hmac.New(sha1.New, secret)
	_, _ = mac.Write(counter[:])
	sum := mac.Sum(nil)
	offset := sum[len(sum)-1] & 0x0f
	binaryCode := (int(sum[offset])&0x7f)<<24 |
		(int(sum[offset+1])&0xff)<<16 |
		(int(sum[offset+2])&0xff)<<8 |
		(int(sum[offset+3]) & 0xff)
	return fmt.Sprintf("%06d", binaryCode%1000000)
}
