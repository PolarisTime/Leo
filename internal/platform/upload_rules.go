package platform

import (
	"context"
	"errors"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

const (
	legacyPageUploadRuleCode = "PAGE_UPLOAD"
	defaultRenamePattern     = "{年月日时分秒}_{random8}"
)

var (
	uploadRuleCodeCleaner = regexp.MustCompile(`[^A-Z0-9]+`)
	uploadRuleModules     = []struct {
		key  string
		name string
	}{
		{"material", "商品资料"},
		{"material-category", "商品类别"},
		{"supplier", "供应商"},
		{"customer", "客户"},
		{"project", "项目"},
		{"carrier", "物流商"},
		{"warehouse", "仓库"},
		{"department", "部门"},
		{"purchase-order", "采购订单"},
		{"purchase-inbound", "采购入库"},
		{"sales-order", "销售订单"},
		{"sales-outbound", "销售出库"},
		{"freight-bill", "物流单"},
		{"purchase-contract", "采购合同"},
		{"sales-contract", "销售合同"},
		{"supplier-statement", "供应商对账单"},
		{"customer-statement", "客户对账单"},
		{"freight-statement", "物流对账单"},
		{"receipt", "收款单"},
		{"payment", "付款单"},
		{"invoice-receipt", "收票单"},
		{"invoice-issue", "开票单"},
		{"pending-invoice-receipt-report", "未收票报表"},
		{"ledger-adjustment", "台账调整单"},
		{"receivable-payable", "应收应付"},
		{"general-setting", "通用设置"},
		{"company-setting", "公司信息"},
		{"permission", "权限管理"},
		{"user-account", "用户账户"},
		{"role-setting", "角色权限配置"},
		{"role-action-editor", "角色权限配置"},
		{"print-template", "打印模板"},
		{"operation-log", "操作日志"},
		{"session", "会话管理"},
		{"api-key", "API Key 管理"},
		{"security-key", "安全密钥管理"},
		{"database", "数据库管理"},
		{"io-report", "出入库报表"},
		{"inventory-report", "库存报表"},
	}
)

type UploadRuleService struct {
	db          *pgxpool.Pool
	idGenerator *IDGenerator
}

func NewUploadRuleService(db *pgxpool.Pool, machineID int64) UploadRuleService {
	return UploadRuleService{db: db, idGenerator: NewIDGenerator(machineID)}
}

func (s UploadRuleService) Detail(ctx context.Context, moduleKey string) (UploadRuleResponse, error) {
	if s.db == nil {
		return UploadRuleResponse{}, errors.New("database client is not configured")
	}
	return s.detail(ctx, moduleKey)
}

func (s UploadRuleService) Update(ctx context.Context, moduleKey string, request UploadRuleRequest) (UploadRuleResponse, error) {
	if s.db == nil {
		return UploadRuleResponse{}, errors.New("database client is not configured")
	}
	moduleKey, err := normalizeUploadRuleModuleKey(moduleKey)
	if err != nil {
		return UploadRuleResponse{}, err
	}
	request, err = normalizeUploadRuleRequest(request)
	if err != nil {
		return UploadRuleResponse{}, err
	}
	if _, err := previewUploadFileNameWithOriginal(request.RenamePattern, "sample-contract.pdf"); err != nil {
		return UploadRuleResponse{}, err
	}
	current, err := s.ruleByModuleKey(ctx, moduleKey)
	if errors.Is(err, pgx.ErrNoRows) {
		defaultRule, err := s.defaultRule(ctx, moduleKey)
		if err != nil {
			return UploadRuleResponse{}, err
		}
		id := s.idGenerator.Next()
		_, err = s.db.Exec(ctx, `
			INSERT INTO sys_upload_rule (
				id, rule_code, rule_name, rename_pattern, status, remark,
				created_by, created_name, deleted_flag, module_key
			) VALUES ($1, $2, $3, $4, $5, $6, 0, 'system', false, $7)
		`, id, defaultRule.RuleCode, defaultRule.RuleName, request.RenamePattern, request.Status, optionalNullString(request.Remark), moduleKey)
		if err != nil {
			if isUniqueViolation(err) {
				return UploadRuleResponse{}, NewAuthError(AuthErrorBusiness, "上传规则已存在")
			}
			return UploadRuleResponse{}, err
		}
		return s.detail(ctx, moduleKey)
	}
	if err != nil {
		return UploadRuleResponse{}, err
	}
	_, err = s.db.Exec(ctx, `
		UPDATE sys_upload_rule
		   SET rename_pattern = $2,
		       status = $3,
		       remark = $4,
		       updated_by = 0,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, current.ID, request.RenamePattern, request.Status, optionalNullString(request.Remark))
	if err != nil {
		return UploadRuleResponse{}, err
	}
	return s.detail(ctx, moduleKey)
}

func (s UploadRuleService) Summaries(ctx context.Context) ([]GeneralSettingResponse, error) {
	if s.db == nil {
		return nil, errors.New("database client is not configured")
	}
	moduleKeys := orderedUploadRuleModuleKeys()
	result := make([]GeneralSettingResponse, 0, len(moduleKeys))
	for _, moduleKey := range moduleKeys {
		detail, err := s.detail(ctx, moduleKey)
		if err != nil {
			return nil, err
		}
		result = append(result, GeneralSettingResponse{
			ID:           detail.ID,
			SettingCode:  detail.RuleCode,
			SettingName:  detail.RuleName,
			BillName:     detail.ModuleName,
			Prefix:       detail.RenamePattern,
			SampleNo:     detail.PreviewFileName,
			Status:       detail.Status,
			Remark:       detail.Remark,
			RuleType:     noRuleTypeUpload,
			ModuleKey:    detail.ModuleKey,
			SerialLength: 0,
		})
	}
	return result, nil
}

func (s UploadRuleService) detail(ctx context.Context, moduleKey string) (UploadRuleResponse, error) {
	moduleKey, err := normalizeUploadRuleModuleKey(moduleKey)
	if err != nil {
		return UploadRuleResponse{}, err
	}
	row, err := s.ruleByModuleKey(ctx, moduleKey)
	if errors.Is(err, pgx.ErrNoRows) {
		return s.defaultRule(ctx, moduleKey)
	}
	if err != nil {
		return UploadRuleResponse{}, err
	}
	return uploadRuleResponse(row), nil
}

func (s UploadRuleService) ruleByModuleKey(ctx context.Context, moduleKey string) (uploadRuleRecord, error) {
	var row uploadRuleRecord
	err := s.db.QueryRow(ctx, `
		SELECT id, module_key, rule_code, rule_name, rename_pattern,
		       status, COALESCE(remark, '')
		  FROM sys_upload_rule
		 WHERE module_key = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, moduleKey).Scan(&row.ID, &row.ModuleKey, &row.RuleCode, &row.RuleName, &row.RenamePattern, &row.Status, &row.Remark)
	if isUndefinedTable(err) {
		return uploadRuleRecord{}, pgx.ErrNoRows
	}
	return row, err
}

func (s UploadRuleService) defaultRule(ctx context.Context, moduleKey string) (UploadRuleResponse, error) {
	pattern, remark := defaultUploadRuleValues()
	if legacy, err := s.legacyRule(ctx); err == nil {
		if strings.TrimSpace(legacy.RenamePattern) != "" {
			pattern = legacy.RenamePattern
		}
		if strings.TrimSpace(legacy.Remark) != "" {
			remark = legacy.Remark
		}
	} else if !errors.Is(err, pgx.ErrNoRows) {
		return UploadRuleResponse{}, err
	}
	moduleName := uploadRuleModuleName(moduleKey)
	preview, err := previewUploadFileNameWithOriginal(pattern, "sample-contract.pdf")
	if err != nil {
		return UploadRuleResponse{}, err
	}
	return UploadRuleResponse{
		ModuleKey:       moduleKey,
		ModuleName:      moduleName,
		RuleCode:        buildUploadRuleCode(moduleKey),
		RuleName:        moduleName + "上传命名规则",
		RenamePattern:   pattern,
		Status:          noRuleStatusNormal,
		Remark:          remark,
		PreviewFileName: preview,
	}, nil
}

func (s UploadRuleService) legacyRule(ctx context.Context) (uploadRuleRecord, error) {
	var row uploadRuleRecord
	err := s.db.QueryRow(ctx, `
		SELECT id, module_key, rule_code, rule_name, rename_pattern,
		       status, COALESCE(remark, '')
		  FROM sys_upload_rule
		 WHERE rule_code = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, legacyPageUploadRuleCode).Scan(&row.ID, &row.ModuleKey, &row.RuleCode, &row.RuleName, &row.RenamePattern, &row.Status, &row.Remark)
	if isUndefinedTable(err) {
		return uploadRuleRecord{}, pgx.ErrNoRows
	}
	return row, err
}

type uploadRuleRecord struct {
	ID            int64
	ModuleKey     string
	RuleCode      string
	RuleName      string
	RenamePattern string
	Status        string
	Remark        string
}

func uploadRuleResponse(row uploadRuleRecord) UploadRuleResponse {
	preview, _ := previewUploadFileNameWithOriginal(row.RenamePattern, "sample-contract.pdf")
	moduleKey := row.ModuleKey
	if strings.TrimSpace(moduleKey) == "" {
		moduleKey = "general-setting"
	}
	return UploadRuleResponse{
		ID:              row.ID,
		ModuleKey:       moduleKey,
		ModuleName:      uploadRuleModuleName(moduleKey),
		RuleCode:        row.RuleCode,
		RuleName:        row.RuleName,
		RenamePattern:   row.RenamePattern,
		Status:          row.Status,
		Remark:          row.Remark,
		PreviewFileName: preview,
	}
}

func normalizeUploadRuleRequest(request UploadRuleRequest) (UploadRuleRequest, error) {
	request.RenamePattern = strings.TrimSpace(request.RenamePattern)
	request.Status = strings.TrimSpace(request.Status)
	request.Remark = strings.TrimSpace(request.Remark)
	if request.RenamePattern == "" {
		return UploadRuleRequest{}, NewAuthError(AuthErrorValidation, "上传命名规则不能为空")
	}
	if request.Status != noRuleStatusNormal && request.Status != noRuleStatusDisabled {
		return UploadRuleRequest{}, NewAuthError(AuthErrorValidation, "上传规则状态不合法")
	}
	if len(request.RenamePattern) > 255 || len(request.Status) > 16 || len(request.Remark) > 255 {
		return UploadRuleRequest{}, NewAuthError(AuthErrorValidation, "字段长度超出限制")
	}
	return request, nil
}

func normalizeUploadRuleModuleKey(moduleKey string) (string, error) {
	moduleKey = strings.TrimSpace(moduleKey)
	if moduleKey == "" {
		return "general-setting", nil
	}
	for _, key := range orderedUploadRuleModuleKeys() {
		if key == moduleKey {
			return moduleKey, nil
		}
	}
	return "", NewAuthError(AuthErrorValidation, "页面模块不合法")
}

func orderedUploadRuleModuleKeys() []string {
	result := make([]string, 0, len(uploadRuleModules))
	for _, entry := range uploadRuleModules {
		result = append(result, entry.key)
	}
	return result
}

func buildUploadRuleCode(moduleKey string) string {
	normalized := strings.ToUpper(strings.TrimSpace(moduleKey))
	normalized = uploadRuleCodeCleaner.ReplaceAllString(normalized, "_")
	normalized = strings.Trim(normalized, "_")
	if normalized == "" {
		normalized = "GENERAL_SETTING"
	}
	return "PAGE_UPLOAD_" + normalized
}

func defaultUploadRuleValues() (string, string) {
	return defaultRenamePattern, "适用于页面选择文件和剪贴板粘贴上传"
}

func previewUploadFileNameWithOriginal(pattern string, original string) (string, error) {
	pattern = strings.TrimSpace(pattern)
	if pattern == "" {
		return "", NewAuthError(AuthErrorValidation, "上传命名规则不能为空")
	}
	extension := "pdf"
	name := "sample-contract"
	if strings.TrimSpace(original) != "" {
		base := filepath.Base(strings.TrimSpace(original))
		ext := filepath.Ext(base)
		if ext != "" {
			name = strings.TrimSuffix(base, ext)
			extension = strings.TrimPrefix(ext, ".")
		} else if base != "" {
			name = base
		}
	}
	now := time.Date(2026, 4, 24, 12, 30, 45, 0, time.Local)
	result := pattern
	replacements := map[string]string{
		"{originName}":     name,
		"{originalName}":   name,
		"{原文件名}":           name,
		"{ext}":            extension,
		"{扩展名}":            extension,
		"{yyyy}":           now.Format("2006"),
		"{年月日}":            now.Format("20060102"),
		"{yyyyMMdd}":       now.Format("20060102"),
		"{HHmmss}":         now.Format("150405"),
		"{年月日时分秒}":         now.Format("20060102150405"),
		"{yyyyMMddHHmmss}": now.Format("20060102150405"),
		"{timestamp}":      "1777005045000",
		"{random8}":        "preview1",
	}
	for key, value := range replacements {
		result = strings.ReplaceAll(result, key, value)
	}
	result = sanitizeUploadBaseName(result)
	if strings.TrimSpace(result) == "" {
		return "", NewAuthError(AuthErrorValidation, "上传命名规则不合法")
	}
	if extension != "" && !strings.HasSuffix(strings.ToLower(result), "."+strings.ToLower(extension)) {
		result += "." + extension
	}
	return result, nil
}

func uploadRuleModuleLabel(moduleKey string) string {
	for _, entry := range uploadRuleModules {
		if entry.key == moduleKey {
			return entry.name
		}
	}
	return moduleKey
}

func sanitizeUploadBaseName(value string) string {
	replacer := strings.NewReplacer("\\", "_", "/", "_", ":", "_", "*", "_", "?", "_", "\"", "_", "<", "_", ">", "_", "|", "_")
	value = strings.TrimSpace(replacer.Replace(value))
	value = strings.Join(strings.Fields(value), "_")
	for strings.Contains(value, "__") {
		value = strings.ReplaceAll(value, "__", "_")
	}
	return strings.Trim(value, "_.")
}

func isUndefinedTable(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "42P01"
}

func isUndefinedColumn(err error) bool {
	var pgErr *pgconn.PgError
	return errors.As(err, &pgErr) && pgErr.Code == "42703"
}
