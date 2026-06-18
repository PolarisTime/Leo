package platform

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"log/slog"
	"regexp"
	"strings"
	"sync"
	"time"
	"unicode/utf8"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"golang.org/x/crypto/bcrypt"
)

const (
	adminRoleCode             = "ADMIN"
	defaultAdminScope         = "全部数据"
	defaultCompanyStatus      = "正常"
	setupRemark               = "网页首次初始化创建"
	oobeCompletedSwitch       = "SYS_OOBE_COMPLETED"
	oobeTotpSecretTTL         = 10 * time.Minute
	defaultDepartmentCode     = "DEPT001"
	uniqueViolationSQLState   = "23505"
	defaultNoRulePrefix       = "SYS"
	defaultNoRuleDateRule     = "yyyy"
	defaultNoRuleSerialLength = 1
	setupAdvisoryLockKey      = int64(700500000000000901)
)

type SetupService struct {
	db          *pgxpool.Pool
	logger      *slog.Logger
	idGenerator *IDGenerator
}

type SetupStatus struct {
	SetupRequired     bool
	AdminConfigured   bool
	CompanyConfigured bool
}

type InitialSetupTotpSetupRequest struct {
	LoginName string `json:"loginName"`
}

type InitialSetupAdminRequest struct {
	LoginName string `json:"loginName"`
	Password  string `json:"password"`
	UserName  string `json:"userName"`
	Mobile    string `json:"mobile"`
}

type InitialSetupAdminSubmitRequest struct {
	Admin      *InitialSetupAdminRequest `json:"admin"`
	TotpSecret string                    `json:"totpSecret"`
	TotpCode   string                    `json:"totpCode"`
}

type InitialSetupCompanyRequest struct {
	CompanyName string   `json:"companyName"`
	TaxNo       string   `json:"taxNo"`
	BankName    string   `json:"bankName"`
	BankAccount string   `json:"bankAccount"`
	TaxRate     *float64 `json:"taxRate"`
	Remark      string   `json:"remark"`
}

type InitialSetupSubmitRequest struct {
	Admin   *InitialSetupAdminSubmitRequest `json:"admin"`
	Company *InitialSetupCompanyRequest     `json:"company"`
}

type InitialSetupSubmitResponse struct {
	AdminLoginName string `json:"adminLoginName"`
	CompanyName    string `json:"companyName"`
}

type setupTotpSecretEntry struct {
	secret    string
	expiresAt time.Time
}

type setupPreparedAdmin struct {
	loginName           string
	passwordHash        string
	userName            string
	mobile              string
	encryptedTotpSecret string
}

var (
	setupLoginNamePattern    = regexp.MustCompile(`^[A-Za-z0-9_.@-]+$`)
	setupMobilePattern       = regexp.MustCompile(`^1\d{10}$`)
	setupTotpSecretsMu       sync.Mutex
	setupTotpSecrets         = map[string]setupTotpSecretEntry{}
	setupFallbackIDGenerator = NewIDGenerator(0)
	setupWriteMu             sync.Mutex
)

func NewSetupService(db *pgxpool.Pool, logger *slog.Logger, machineID ...int64) SetupService {
	var selectedMachineID int64
	if len(machineID) > 0 {
		selectedMachineID = machineID[0]
	}
	return SetupService{db: db, logger: logger, idGenerator: NewIDGenerator(selectedMachineID)}
}

func (s SetupService) Status(ctx context.Context) (SetupStatus, error) {
	adminConfigured, err := s.isAdminConfigured(ctx)
	if err != nil {
		return SetupStatus{}, err
	}
	companyConfigured, err := s.isCompanyConfigured(ctx)
	if err != nil {
		return SetupStatus{}, err
	}
	return SetupStatus{
		SetupRequired:     !adminConfigured || !companyConfigured,
		AdminConfigured:   adminConfigured,
		CompanyConfigured: companyConfigured,
	}, nil
}

func (s SetupService) SetupAdminTotp(ctx context.Context, request InitialSetupTotpSetupRequest) (TotpSetupResponse, error) {
	setupWriteMu.Lock()
	defer setupWriteMu.Unlock()
	if err := s.ensureReady(); err != nil {
		return TotpSetupResponse{}, err
	}
	if err := s.assertOobeNotCompleted(ctx); err != nil {
		return TotpSetupResponse{}, err
	}
	adminConfigured, err := s.isAdminConfigured(ctx)
	if err != nil {
		return TotpSetupResponse{}, err
	}
	if adminConfigured {
		return TotpSetupResponse{}, NewAuthError(AuthErrorBusiness, "管理员账号已完成初始化")
	}

	loginName, err := s.validateLoginName(request.LoginName, "管理员登录账号不能为空")
	if err != nil {
		return TotpSetupResponse{}, err
	}
	secret, err := generateTotpSecret()
	if err != nil {
		return TotpSetupResponse{}, err
	}
	cacheSetupTotpSecret(loginName, secret)
	qrCodeBase64, err := generateTotpQrCodeBase64(secret, loginName, setupTotpIssuer())
	if err != nil {
		return TotpSetupResponse{}, err
	}
	return TotpSetupResponse{QrCodeBase64: qrCodeBase64, Secret: secret}, nil
}

func (s SetupService) Initialize(ctx context.Context, request InitialSetupSubmitRequest) (InitialSetupSubmitResponse, error) {
	setupWriteMu.Lock()
	defer setupWriteMu.Unlock()
	return initializeSetup(ctx, s, request)
}

type setupInitializeOperations interface {
	ensureReady() error
	isAdminConfigured(ctx context.Context) (bool, error)
	isCompanyConfigured(ctx context.Context) (bool, error)
	prepareAdmin(ctx context.Context, request *InitialSetupAdminSubmitRequest) (setupPreparedAdmin, error)
	withTx(ctx context.Context, fn func(pgx.Tx) error) error
	isAdminConfiguredTx(ctx context.Context, tx pgx.Tx) (bool, error)
	isCompanyConfiguredTx(ctx context.Context, tx pgx.Tx) (bool, error)
	createAdminTx(ctx context.Context, tx pgx.Tx, admin setupPreparedAdmin) (string, error)
	createCompanyRecordTx(ctx context.Context, tx pgx.Tx, request *InitialSetupCompanyRequest) (string, error)
	resolveExistingAdminLoginNameTx(ctx context.Context, tx pgx.Tx) (string, error)
	resolveExistingCompanyNameTx(ctx context.Context, tx pgx.Tx) (string, error)
	ensureOobeCompletedSwitchTx(ctx context.Context, tx pgx.Tx) error
}

func initializeSetup(ctx context.Context, ops setupInitializeOperations, request InitialSetupSubmitRequest) (InitialSetupSubmitResponse, error) {
	if err := ops.ensureReady(); err != nil {
		return InitialSetupSubmitResponse{}, err
	}
	initialAdminConfigured, err := ops.isAdminConfigured(ctx)
	if err != nil {
		return InitialSetupSubmitResponse{}, err
	}
	initialCompanyConfigured, err := ops.isCompanyConfigured(ctx)
	if err != nil {
		return InitialSetupSubmitResponse{}, err
	}
	if initialAdminConfigured && initialCompanyConfigured {
		return InitialSetupSubmitResponse{}, NewAuthError(AuthErrorBusiness, "系统已完成首次初始化")
	}
	var preparedAdmin setupPreparedAdmin
	if !initialAdminConfigured {
		if request.Admin == nil {
			return InitialSetupSubmitResponse{}, NewAuthError(AuthErrorValidation, "请填写管理员账号信息")
		}
		preparedAdmin, err = ops.prepareAdmin(ctx, request.Admin)
		if err != nil {
			return InitialSetupSubmitResponse{}, err
		}
	}

	var response InitialSetupSubmitResponse
	err = ops.withTx(ctx, func(tx pgx.Tx) error {
		adminConfigured, err := ops.isAdminConfiguredTx(ctx, tx)
		if err != nil {
			return err
		}
		companyConfigured, err := ops.isCompanyConfiguredTx(ctx, tx)
		if err != nil {
			return err
		}
		if adminConfigured && companyConfigured {
			return NewAuthError(AuthErrorBusiness, "系统已完成首次初始化")
		}

		if !adminConfigured {
			adminLoginName, err := ops.createAdminTx(ctx, tx, preparedAdmin)
			if err != nil {
				return err
			}
			response.AdminLoginName = adminLoginName
		} else {
			adminLoginName, err := ops.resolveExistingAdminLoginNameTx(ctx, tx)
			if err != nil {
				return err
			}
			response.AdminLoginName = adminLoginName
		}

		if !companyConfigured {
			companyName, err := ops.createCompanyRecordTx(ctx, tx, request.Company)
			if err != nil {
				return err
			}
			response.CompanyName = companyName
		} else {
			companyName, err := ops.resolveExistingCompanyNameTx(ctx, tx)
			if err != nil {
				return err
			}
			response.CompanyName = companyName
		}

		adminConfigured, err = ops.isAdminConfiguredTx(ctx, tx)
		if err != nil {
			return err
		}
		companyConfigured, err = ops.isCompanyConfiguredTx(ctx, tx)
		if err != nil {
			return err
		}
		if adminConfigured && companyConfigured {
			return ops.ensureOobeCompletedSwitchTx(ctx, tx)
		}
		return nil
	})
	if err != nil {
		return InitialSetupSubmitResponse{}, err
	}
	return response, nil
}

func (s SetupService) ConfigureAdmin(ctx context.Context, request InitialSetupAdminSubmitRequest) (InitialSetupSubmitResponse, error) {
	setupWriteMu.Lock()
	defer setupWriteMu.Unlock()
	if err := s.ensureReady(); err != nil {
		return InitialSetupSubmitResponse{}, err
	}
	if err := s.assertOobeNotCompleted(ctx); err != nil {
		return InitialSetupSubmitResponse{}, err
	}
	adminConfigured, err := s.isAdminConfigured(ctx)
	if err != nil {
		return InitialSetupSubmitResponse{}, err
	}
	if adminConfigured {
		return InitialSetupSubmitResponse{}, NewAuthError(AuthErrorBusiness, "管理员账号已完成初始化")
	}
	preparedAdmin, err := s.prepareAdmin(ctx, &request)
	if err != nil {
		return InitialSetupSubmitResponse{}, err
	}

	var adminLoginName string
	err = s.withTx(ctx, func(tx pgx.Tx) error {
		if err := s.assertOobeNotCompletedTx(ctx, tx); err != nil {
			return err
		}
		adminConfigured, err := s.isAdminConfiguredTx(ctx, tx)
		if err != nil {
			return err
		}
		if adminConfigured {
			return NewAuthError(AuthErrorBusiness, "管理员账号已完成初始化")
		}
		adminLoginName, err = s.createAdminTx(ctx, tx, preparedAdmin)
		return err
	})
	if err != nil {
		return InitialSetupSubmitResponse{}, err
	}
	return InitialSetupSubmitResponse{AdminLoginName: adminLoginName}, nil
}

func (s SetupService) ConfigureCompany(ctx context.Context, request InitialSetupCompanyRequest) (InitialSetupSubmitResponse, error) {
	setupWriteMu.Lock()
	defer setupWriteMu.Unlock()
	if err := s.ensureReady(); err != nil {
		return InitialSetupSubmitResponse{}, err
	}
	if err := s.assertOobeNotCompleted(ctx); err != nil {
		return InitialSetupSubmitResponse{}, err
	}
	adminConfigured, err := s.isAdminConfigured(ctx)
	if err != nil {
		return InitialSetupSubmitResponse{}, err
	}
	if !adminConfigured {
		return InitialSetupSubmitResponse{}, NewAuthError(AuthErrorBusiness, "请先完成管理员账号初始化")
	}
	companyConfigured, err := s.isCompanyConfigured(ctx)
	if err != nil {
		return InitialSetupSubmitResponse{}, err
	}
	if companyConfigured {
		return InitialSetupSubmitResponse{}, NewAuthError(AuthErrorBusiness, "公司主体已完成初始化")
	}

	var response InitialSetupSubmitResponse
	err = s.withTx(ctx, func(tx pgx.Tx) error {
		if err := s.assertOobeNotCompletedTx(ctx, tx); err != nil {
			return err
		}
		adminConfigured, err := s.isAdminConfiguredTx(ctx, tx)
		if err != nil {
			return err
		}
		if !adminConfigured {
			return NewAuthError(AuthErrorBusiness, "请先完成管理员账号初始化")
		}
		companyConfigured, err := s.isCompanyConfiguredTx(ctx, tx)
		if err != nil {
			return err
		}
		if companyConfigured {
			return NewAuthError(AuthErrorBusiness, "公司主体已完成初始化")
		}

		adminLoginName, err := s.resolveExistingAdminLoginNameTx(ctx, tx)
		if err != nil {
			return err
		}
		companyName, err := s.createCompanyRecordTx(ctx, tx, &request)
		if err != nil {
			return err
		}
		if err := s.ensureOobeCompletedSwitchTx(ctx, tx); err != nil {
			return err
		}
		response = InitialSetupSubmitResponse{
			AdminLoginName: adminLoginName,
			CompanyName:    companyName,
		}
		return nil
	})
	if err != nil {
		return InitialSetupSubmitResponse{}, err
	}
	return response, nil
}

func (s SetupService) isAdminConfigured(ctx context.Context) (bool, error) {
	if s.db == nil {
		return false, nil
	}
	var exists bool
	err := s.db.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1
			  FROM sys_role role
			  JOIN sys_user_role user_role
			    ON user_role.role_id = role.id
			   AND user_role.deleted_flag = false
			  JOIN sys_user user_account
			    ON user_account.id = user_role.user_id
			   AND user_account.deleted_flag = false
			 WHERE role.role_code = $1
			   AND role.deleted_flag = false
		)
	`, adminRoleCode).Scan(&exists)
	return exists, err
}

func (s SetupService) isCompanyConfigured(ctx context.Context) (bool, error) {
	if s.db == nil {
		return false, nil
	}
	var exists bool
	err := s.db.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1
			  FROM sys_company_setting
			 WHERE deleted_flag = false
		)
	`).Scan(&exists)
	return exists, err
}

func (s SetupService) isAdminConfiguredTx(ctx context.Context, tx pgx.Tx) (bool, error) {
	var exists bool
	err := tx.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1
			  FROM sys_role role
			  JOIN sys_user_role user_role
			    ON user_role.role_id = role.id
			   AND user_role.deleted_flag = false
			  JOIN sys_user user_account
			    ON user_account.id = user_role.user_id
			   AND user_account.deleted_flag = false
			 WHERE role.role_code = $1
			   AND role.deleted_flag = false
		)
	`, adminRoleCode).Scan(&exists)
	return exists, err
}

func (s SetupService) isCompanyConfiguredTx(ctx context.Context, tx pgx.Tx) (bool, error) {
	var exists bool
	err := tx.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1
			  FROM sys_company_setting
			 WHERE deleted_flag = false
		)
	`).Scan(&exists)
	return exists, err
}

func (s SetupService) ensureReady() error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	return nil
}

func (s SetupService) withTx(ctx context.Context, fn func(pgx.Tx) error) error {
	tx, err := s.db.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return err
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()
	if err := s.lockSetupTx(ctx, tx); err != nil {
		return err
	}
	if err := fn(tx); err != nil {
		return err
	}
	return tx.Commit(ctx)
}

func (s SetupService) assertOobeNotCompleted(ctx context.Context) error {
	status, err := s.Status(ctx)
	if err != nil {
		return err
	}
	if !status.SetupRequired {
		return NewAuthError(AuthErrorForbidden, "系统已完成初始化，该接口已禁用")
	}
	return nil
}

func (s SetupService) assertOobeNotCompletedTx(ctx context.Context, tx pgx.Tx) error {
	adminConfigured, err := s.isAdminConfiguredTx(ctx, tx)
	if err != nil {
		return err
	}
	companyConfigured, err := s.isCompanyConfiguredTx(ctx, tx)
	if err != nil {
		return err
	}
	if adminConfigured && companyConfigured {
		return NewAuthError(AuthErrorForbidden, "系统已完成初始化，该接口已禁用")
	}
	return nil
}

func (s SetupService) prepareAdmin(ctx context.Context, request *InitialSetupAdminSubmitRequest) (setupPreparedAdmin, error) {
	if request == nil || request.Admin == nil {
		return setupPreparedAdmin{}, NewAuthError(AuthErrorValidation, "请填写管理员账号信息")
	}
	admin := request.Admin
	loginName, err := s.validateLoginName(admin.LoginName, "管理员登录账号不能为空")
	if err != nil {
		return setupPreparedAdmin{}, err
	}
	password, err := requireSetupText(admin.Password, "管理员密码不能为空")
	if err != nil {
		return setupPreparedAdmin{}, err
	}
	if runeCount(password) > 128 {
		return setupPreparedAdmin{}, NewAuthError(AuthErrorValidation, "管理员密码格式不正确")
	}
	if runeCount(password) < 8 {
		return setupPreparedAdmin{}, NewAuthError(AuthErrorValidation, "管理员密码至少8位")
	}
	userName, err := requireSetupTextWithMax(admin.UserName, 64, "管理员姓名不能为空", "管理员姓名格式不正确")
	if err != nil {
		return setupPreparedAdmin{}, err
	}
	mobile, err := validateSetupMobile(admin.Mobile)
	if err != nil {
		return setupPreparedAdmin{}, err
	}
	totpCode, err := requireSetupText(request.TotpCode, "请输入管理员 2FA 验证码")
	if err != nil {
		return setupPreparedAdmin{}, err
	}
	if !isSixDigitCode(totpCode) {
		return setupPreparedAdmin{}, NewAuthError(AuthErrorValidation, "2FA 验证码必须为6位数字")
	}
	totpSecret, ok := resolveCachedSetupTotpSecret(loginName)
	if !ok {
		return setupPreparedAdmin{}, NewAuthError(AuthErrorValidation, "请先生成并绑定管理员 2FA")
	}
	if !verifyTOTP(totpSecret, totpCode, time.Now()) {
		return setupPreparedAdmin{}, NewAuthError(AuthErrorValidation, "管理员 2FA 验证码不正确")
	}
	hashed, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return setupPreparedAdmin{}, err
	}
	encryptedSecret, err := s.encryptSetupTotpSecret(ctx, totpSecret)
	if err != nil {
		return setupPreparedAdmin{}, err
	}
	return setupPreparedAdmin{
		loginName:           loginName,
		passwordHash:        string(hashed),
		userName:            userName,
		mobile:              mobile,
		encryptedTotpSecret: encryptedSecret,
	}, nil
}

func (s SetupService) createAdminTx(ctx context.Context, tx pgx.Tx, admin setupPreparedAdmin) (string, error) {
	adminRoleID, err := s.lockAdminRoleTx(ctx, tx)
	if err != nil {
		return "", err
	}
	adminConfigured, err := s.isAdminConfiguredTx(ctx, tx)
	if err != nil {
		return "", err
	}
	if adminConfigured {
		return "", NewAuthError(AuthErrorBusiness, "管理员账号已完成初始化")
	}
	loginNameExists, err := s.loginNameExistsTx(ctx, tx, admin.loginName)
	if err != nil {
		return "", err
	}
	if loginNameExists {
		return "", NewAuthError(AuthErrorValidation, "管理员登录账号已存在")
	}
	departmentID, departmentName, err := s.defaultDepartmentTx(ctx, tx)
	if err != nil {
		return "", err
	}

	userID := s.nextID()
	_, err = tx.Exec(ctx, `
		INSERT INTO sys_user (
			id, login_name, password_hash, user_name, mobile,
			department_id, department_name, data_scope, permission_summary,
			status, remark, totp_secret, totp_enabled, require_totp_setup,
			created_by, created_name, deleted_flag, version, preferences_json
		) VALUES (
			$1, $2, $3, $4, $5,
			$6, $7, $8, '',
			'NORMAL', $9, $10, true, false,
			0, 'system', false, 0, '{}'::jsonb
		)
	`, userID, admin.loginName, admin.passwordHash, admin.userName, setupNullString(admin.mobile), setupNullInt64(departmentID), setupNullString(departmentName), defaultAdminScope, setupRemark, admin.encryptedTotpSecret)
	if err != nil {
		if isUniqueViolation(err) {
			return "", NewAuthError(AuthErrorValidation, "管理员登录账号已存在")
		}
		return "", err
	}
	if err := s.bindAdminRoleTx(ctx, tx, userID, adminRoleID); err != nil {
		return "", err
	}
	evictCachedSetupTotpSecret(admin.loginName)
	return admin.loginName, nil
}

func (s SetupService) createCompanyRecordTx(ctx context.Context, tx pgx.Tx, request *InitialSetupCompanyRequest) (string, error) {
	if request == nil {
		return "", NewAuthError(AuthErrorValidation, "请填写公司主体信息")
	}
	companyName, err := requireSetupTextWithMax(request.CompanyName, 128, "公司名称不能为空", "公司名称格式不正确")
	if err != nil {
		return "", err
	}
	taxNo, err := requireSetupTextWithMax(request.TaxNo, 64, "税号不能为空", "税号格式不正确")
	if err != nil {
		return "", err
	}
	bankName, err := requireSetupTextWithMax(request.BankName, 128, "开户银行不能为空", "开户银行格式不正确")
	if err != nil {
		return "", err
	}
	bankAccount, err := requireSetupTextWithMax(request.BankAccount, 64, "银行账号不能为空", "银行账号格式不正确")
	if err != nil {
		return "", err
	}
	if request.TaxRate == nil {
		return "", NewAuthError(AuthErrorValidation, "税率不能为空")
	}
	taxRate := *request.TaxRate
	if taxRate < 0 {
		return "", NewAuthError(AuthErrorValidation, "税率不能小于0")
	}
	remark := strings.TrimSpace(request.Remark)
	if runeCount(remark) > 255 {
		return "", NewAuthError(AuthErrorValidation, "备注格式不正确")
	}
	storedRemark := remark
	if storedRemark == "" {
		storedRemark = setupRemark
	}
	accountsJSON, err := s.buildSettlementAccountsJSON(companyName, bankName, bankAccount, defaultCompanyStatus, storedRemark)
	if err != nil {
		return "", err
	}

	_, err = tx.Exec(ctx, `
		INSERT INTO sys_company_setting (
			id, company_name, tax_no, bank_name, bank_account,
			tax_rate, settlement_accounts_json, status, remark,
			created_by, created_name, deleted_flag
		) VALUES (
			$1, $2, $3, $4, $5,
			$6, $7::jsonb, $8, $9,
			0, 'system', false
		)
	`, s.nextID(), companyName, taxNo, bankName, bankAccount, taxRate, accountsJSON, defaultCompanyStatus, storedRemark)
	if err != nil {
		if isUniqueViolation(err) {
			return "", NewAuthError(AuthErrorValidation, "公司信息已存在，请刷新页面后重试")
		}
		return "", err
	}
	if err := s.upsertDefaultTaxRateSettingTx(ctx, tx, taxRate); err != nil {
		return "", err
	}
	return companyName, nil
}

func (s SetupService) validateLoginName(value string, requiredMessage string) (string, error) {
	loginName, err := requireSetupTextWithMax(value, 64, requiredMessage, "管理员登录账号格式不正确")
	if err != nil {
		return "", err
	}
	if !setupLoginNamePattern.MatchString(loginName) {
		return "", NewAuthError(AuthErrorValidation, "管理员登录账号格式不正确")
	}
	return loginName, nil
}

func requireSetupText(value string, message string) (string, error) {
	return requireSetupTextWithMax(value, 0, message, "")
}

func requireSetupTextWithMax(value string, max int, requiredMessage string, tooLongMessage string) (string, error) {
	value = strings.TrimSpace(value)
	if value == "" {
		return "", NewAuthError(AuthErrorValidation, requiredMessage)
	}
	if max > 0 && runeCount(value) > max {
		if tooLongMessage == "" {
			tooLongMessage = requiredMessage
		}
		return "", NewAuthError(AuthErrorValidation, tooLongMessage)
	}
	return value, nil
}

func validateSetupMobile(value string) (string, error) {
	value = strings.TrimSpace(value)
	if value == "" {
		return "", nil
	}
	if runeCount(value) > 32 || !setupMobilePattern.MatchString(value) {
		return "", NewAuthError(AuthErrorValidation, "管理员手机号格式不正确")
	}
	return value, nil
}

func runeCount(value string) int {
	return utf8.RuneCountInString(value)
}

func cacheSetupTotpSecret(loginName string, secret string) {
	setupTotpSecretsMu.Lock()
	defer setupTotpSecretsMu.Unlock()
	now := time.Now()
	pruneExpiredSetupTotpSecretsLocked(now)
	setupTotpSecrets[loginName] = setupTotpSecretEntry{
		secret:    strings.TrimSpace(secret),
		expiresAt: now.Add(oobeTotpSecretTTL),
	}
}

func resolveCachedSetupTotpSecret(loginName string) (string, bool) {
	setupTotpSecretsMu.Lock()
	defer setupTotpSecretsMu.Unlock()
	now := time.Now()
	pruneExpiredSetupTotpSecretsLocked(now)
	entry, ok := setupTotpSecrets[loginName]
	if !ok || strings.TrimSpace(entry.secret) == "" || entry.expiresAt.Before(now) {
		delete(setupTotpSecrets, loginName)
		return "", false
	}
	return entry.secret, true
}

func evictCachedSetupTotpSecret(loginName string) {
	setupTotpSecretsMu.Lock()
	defer setupTotpSecretsMu.Unlock()
	delete(setupTotpSecrets, loginName)
}

func pruneExpiredSetupTotpSecretsLocked(now time.Time) {
	for loginName, entry := range setupTotpSecrets {
		if entry.expiresAt.Before(now) {
			delete(setupTotpSecrets, loginName)
		}
	}
}

func (s SetupService) lockAdminRoleTx(ctx context.Context, tx pgx.Tx) (int64, error) {
	var roleID int64
	err := tx.QueryRow(ctx, `
		SELECT id
		  FROM sys_role
		 WHERE role_code = $1
		   AND deleted_flag = false
		 LIMIT 1
		 FOR UPDATE
	`, adminRoleCode).Scan(&roleID)
	if errors.Is(err, pgx.ErrNoRows) {
		return 0, NewAuthError(AuthErrorBusiness, "未找到系统管理员角色，请先检查基础数据")
	}
	return roleID, err
}

func (s SetupService) loginNameExistsTx(ctx context.Context, tx pgx.Tx, loginName string) (bool, error) {
	var exists bool
	err := tx.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1
			  FROM sys_user
			 WHERE login_name = $1
			   AND deleted_flag = false
		)
	`, loginName).Scan(&exists)
	return exists, err
}

func (s SetupService) defaultDepartmentTx(ctx context.Context, tx pgx.Tx) (int64, string, error) {
	var id int64
	var name string
	err := tx.QueryRow(ctx, `
		SELECT id, department_name
		  FROM sys_department
		 WHERE department_code = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, defaultDepartmentCode).Scan(&id, &name)
	if errors.Is(err, pgx.ErrNoRows) {
		return 0, "", nil
	}
	return id, name, err
}

func (s SetupService) bindAdminRoleTx(ctx context.Context, tx pgx.Tx, userID int64, roleID int64) error {
	_, err := tx.Exec(ctx, `
		INSERT INTO sys_user_role (
			id, user_id, role_id, created_by, created_name, deleted_flag
		) VALUES (
			$1, $2, $3, 0, 'system', false
		)
		ON CONFLICT (user_id, role_id) DO UPDATE
		   SET deleted_flag = false,
		       updated_by = 0,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
	`, s.nextID(), userID, roleID)
	return err
}

func (s SetupService) resolveExistingAdminLoginNameTx(ctx context.Context, tx pgx.Tx) (string, error) {
	var loginName string
	err := tx.QueryRow(ctx, `
		SELECT user_account.login_name
		  FROM sys_role role
		  JOIN sys_user_role user_role
		    ON user_role.role_id = role.id
		   AND user_role.deleted_flag = false
		  JOIN sys_user user_account
		    ON user_account.id = user_role.user_id
		   AND user_account.deleted_flag = false
		 WHERE role.role_code = $1
		   AND role.deleted_flag = false
		 ORDER BY user_account.id ASC
		 LIMIT 1
	`, adminRoleCode).Scan(&loginName)
	if errors.Is(err, pgx.ErrNoRows) {
		return "admin", nil
	}
	return loginName, err
}

func (s SetupService) resolveExistingCompanyNameTx(ctx context.Context, tx pgx.Tx) (string, error) {
	var companyName string
	err := tx.QueryRow(ctx, `
		SELECT company_name
		  FROM sys_company_setting
		 WHERE deleted_flag = false
		 ORDER BY id ASC
		 LIMIT 1
	`).Scan(&companyName)
	if errors.Is(err, pgx.ErrNoRows) {
		return "", nil
	}
	return companyName, err
}

func (s SetupService) buildSettlementAccountsJSON(companyName, bankName, bankAccount, status, remark string) (string, error) {
	accounts := []CompanySettlementAccount{{
		ID:          s.nextID(),
		AccountName: companyName,
		BankName:    bankName,
		BankAccount: bankAccount,
		UsageType:   "通用",
		Status:      status,
		Remark:      remark,
	}}
	raw, err := json.Marshal(accounts)
	if err != nil {
		return "", err
	}
	return string(raw), nil
}

func (s SetupService) upsertDefaultTaxRateSettingTx(ctx context.Context, tx pgx.Tx, taxRate float64) error {
	_, err := tx.Exec(ctx, `
		INSERT INTO sys_no_rule (
			id, setting_code, setting_name, bill_name, prefix, date_rule,
			serial_length, reset_rule, sample_no, status, remark,
			created_by, created_name, deleted_flag
		) VALUES (
			$1, $2, '默认税率', '发票税率', $3, $4,
			$5, 'YEARLY', to_char($6::numeric, 'FM999999990.0000'), '正常', '用于发票默认税率与税额自动计算',
			0, 'system', false
		)
		ON CONFLICT (setting_code) DO UPDATE
		   SET sample_no = EXCLUDED.sample_no,
		       status = '正常',
		       remark = '用于发票默认税率与税额自动计算',
		       deleted_flag = false,
		       updated_by = 0,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
	`, s.nextID(), defaultTaxRateSettingCode, defaultNoRulePrefix, defaultNoRuleDateRule, defaultNoRuleSerialLength, taxRate)
	return err
}

func (s SetupService) ensureOobeCompletedSwitchTx(ctx context.Context, tx pgx.Tx) error {
	_, err := tx.Exec(ctx, `
		INSERT INTO sys_no_rule (
			id, setting_code, setting_name, bill_name, prefix, date_rule,
			serial_length, reset_rule, sample_no, status, remark,
			created_by, created_name, deleted_flag
		) VALUES (
			$1, $2, 'OOBE已完成', '系统初始化', $3, $4,
			$5, 'ONCE', 'COMPLETED', '正常', '首次初始化完成后自动创建，禁止重复执行 OOBE 流程',
			0, 'system', false
		)
		ON CONFLICT (setting_code) DO UPDATE
		   SET sample_no = 'COMPLETED',
		       status = '正常',
		       remark = '首次初始化完成后自动创建，禁止重复执行 OOBE 流程',
		       deleted_flag = false,
		       updated_by = 0,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
	`, s.nextID(), oobeCompletedSwitch, defaultNoRulePrefix, defaultNoRuleDateRule, defaultNoRuleSerialLength)
	return err
}

func (s SetupService) lockSetupTx(ctx context.Context, tx pgx.Tx) error {
	_, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock($1)`, setupAdvisoryLockKey)
	return err
}

func (s SetupService) encryptSetupTotpSecret(ctx context.Context, plainSecret string) (string, error) {
	auth := AuthService{db: s.db}
	return auth.encryptTotpSecret(ctx, plainSecret)
}

func (s SetupService) nextID() int64 {
	if s.idGenerator == nil {
		return setupFallbackIDGenerator.Next()
	}
	return s.idGenerator.Next()
}

func setupTotpIssuer() string {
	auth := AuthService{}
	return auth.totpIssuer()
}

func setupNullString(value string) sql.NullString {
	return nullString(value)
}

func setupNullInt64(value int64) sql.NullInt64 {
	if value == 0 {
		return sql.NullInt64{}
	}
	return sql.NullInt64{Int64: value, Valid: true}
}

func isUniqueViolation(err error) bool {
	type sqlStateError interface {
		SQLState() string
	}
	var stateErr sqlStateError
	if errors.As(err, &stateErr) {
		return stateErr.SQLState() == uniqueViolationSQLState
	}
	return false
}
