package platform

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"sort"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

const (
	noRuleStatusNormal   = "正常"
	noRuleStatusDisabled = "禁用"

	noRuleTypeGeneral = "NO_RULE"
	noRuleTypeUpload  = "UPLOAD_RULE"

	switchCustomerStatementReceiptZero = "SYS_CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER"
	switchSupplierStatementFullPayment = "SYS_SUPPLIER_STATEMENT_FULL_PAYMENT_FROM_PURCHASE"
	switchUseSnowflakeBusinessNo       = "SYS_USE_SNOWFLAKE_ID_AS_BUSINESS_NO"
	switchDetailedOperationLogActions  = "SYS_OPERATION_LOG_DETAILED_PAGE_ACTIONS"
)

var (
	generalSettingOrder = map[string]int{
		"RULE_MAT": 10, "RULE_MC": 20, "RULE_SUP": 30, "RULE_CUST": 40, "RULE_CAR": 50,
		"RULE_WH": 60, "RULE_PO": 70, "RULE_PI": 80, "RULE_SO": 90, "RULE_OB": 100,
		"RULE_FB": 110, "RULE_PC": 120, "RULE_SC": 130, "RULE_SS": 140, "RULE_CS": 150,
		"RULE_FS": 160, "RULE_RC": 170, "RULE_PM": 180, "RULE_SP": 190, "RULE_KP": 200,
		"RULE_BATCH_NO": 210, defaultTaxRateSettingCode: 95, "UI_DEFAULT_LIST_PAGE_SIZE": 98,
		"UI_WEIGHT_ONLY_PURCHASE_INBOUNDS": 100, "UI_WEIGHT_ONLY_SALES_OUTBOUNDS": 110,
		switchCustomerStatementReceiptZero: 120, switchSupplierStatementFullPayment: 130,
		"SYS_OPERATION_LOG_RECORD_ALL_WRITE": 140, switchDetailedOperationLogActions: 150,
		"SYS_OPERATION_LOG_RECORD_AUTH_EVENTS": 160, "SYS_FORCE_USER_TOTP_ON_FIRST_LOGIN": 170,
		"SYS_BATCH_NO_AUTO_GENERATE": 180, "UI_HIDE_AUDITED_LIST_RECORDS": 190,
		"SYS_ADMIN_VIEW_DELETED_RECORDS": 195, "UI_SHOW_SNOWFLAKE_ID": 200, "SYS_LOGIN_CAPTCHA": 205,
		switchUseSnowflakeBusinessNo: 210, "UI_WATERMARK_ENABLED": 220,
		"SYS_WATERMARK_CONTENT": 221, "SYS_WATERMARK_FONT_SIZE": 222, "SYS_WATERMARK_ROTATE": 223,
		"SYS_WATERMARK_COLOR": 224, "SYS_WATERMARK_DENSITY": 225, "PAGE_UPLOAD": 900,
	}
	publicDisplaySwitchCodes = map[string]struct{}{
		"UI_WEIGHT_ONLY_PURCHASE_INBOUNDS": {},
		"UI_WEIGHT_ONLY_SALES_OUTBOUNDS":   {},
		"UI_HIDE_AUDITED_LIST_RECORDS":     {},
		"UI_SHOW_SNOWFLAKE_ID":             {},
	}
	publicClientSettingCodes = map[string]struct{}{
		"UI_WEIGHT_ONLY_PURCHASE_INBOUNDS": {}, "UI_WEIGHT_ONLY_SALES_OUTBOUNDS": {},
		defaultTaxRateSettingCode: {}, switchCustomerStatementReceiptZero: {},
		switchSupplierStatementFullPayment: {}, "UI_SHOW_SNOWFLAKE_ID": {},
		"UI_DEFAULT_LIST_PAGE_SIZE": {}, switchUseSnowflakeBusinessNo: {},
		"UI_WATERMARK_ENABLED": {}, "SYS_WATERMARK_CONTENT": {}, "SYS_WATERMARK_FONT_SIZE": {},
		"SYS_WATERMARK_ROTATE": {}, "SYS_WATERMARK_COLOR": {}, "SYS_WATERMARK_DENSITY": {},
	}
	moduleRuleCodeMap = map[string]string{
		"material": "RULE_MAT", "material-category": "RULE_MC", "material-categories": "RULE_MC",
		"supplier": "RULE_SUP", "customer": "RULE_CUST", "carrier": "RULE_CAR", "warehouse": "RULE_WH",
		"purchase-order": "RULE_PO", "purchase-inbound": "RULE_PI", "sales-order": "RULE_SO",
		"sales-outbound": "RULE_OB", "freight-bill": "RULE_FB", "purchase-contract": "RULE_PC",
		"sales-contract": "RULE_SC", "supplier-statement": "RULE_SS", "customer-statement": "RULE_CS",
		"freight-statement": "RULE_FS", "receipt": "RULE_RC", "payment": "RULE_PM",
		"invoice-receipt": "RULE_SP", "invoice-issue": "RULE_KP", "ledger-adjustment": "RULE_LA",
		"department": "RULE_DEPT",
	}
	detailedOperationLogActions = map[string]struct{}{
		"QUERY": {}, "DETAIL": {}, "CREATE": {}, "EDIT": {}, "DELETE": {}, "AUDIT": {}, "EXPORT": {}, "PRINT": {},
	}
)

type GeneralSettingService struct {
	db          *pgxpool.Pool
	idGenerator *IDGenerator
	uploadRules UploadRuleService
}

func NewGeneralSettingService(db *pgxpool.Pool, machineID int64) GeneralSettingService {
	return GeneralSettingService{db: db, idGenerator: NewIDGenerator(machineID), uploadRules: NewUploadRuleService(db, machineID)}
}

func (s GeneralSettingService) Page(ctx context.Context, query PageQuery, keyword string, status string) (PageResponse[GeneralSettingResponse], error) {
	if s.db == nil {
		return PageResponse[GeneralSettingResponse]{}, errors.New("database client is not configured")
	}
	rows, err := s.generalSettingRows(ctx)
	if err != nil {
		return PageResponse[GeneralSettingResponse]{}, err
	}
	keyword = strings.ToLower(strings.TrimSpace(keyword))
	status = strings.TrimSpace(status)
	filtered := make([]GeneralSettingResponse, 0, len(rows))
	for _, row := range rows {
		if keyword != "" && !generalSettingMatchesKeyword(row, keyword) {
			continue
		}
		if status != "" && row.Status != status {
			continue
		}
		filtered = append(filtered, row)
	}
	sortGeneralSettings(filtered)
	total := int64(len(filtered))
	start := query.Page * query.Size
	if start > len(filtered) {
		start = len(filtered)
	}
	end := start + query.Size
	if end > len(filtered) {
		end = len(filtered)
	}
	return NewPageResponse(filtered[start:end], total, query), nil
}

func (s GeneralSettingService) PublicDisplaySwitches(ctx context.Context) ([]GeneralSettingResponse, error) {
	return s.generalSettingsByCodes(ctx, publicDisplaySwitchCodes)
}

func (s GeneralSettingService) PublicClientSettings(ctx context.Context) ([]GeneralSettingResponse, error) {
	return s.generalSettingsByCodes(ctx, publicClientSettingCodes)
}

func (s GeneralSettingService) StatementGeneratorRules(ctx context.Context) (StatementGeneratorRulesResponse, error) {
	customer, err := s.switchEnabled(ctx, switchCustomerStatementReceiptZero)
	if err != nil {
		return StatementGeneratorRulesResponse{}, err
	}
	supplier, err := s.switchEnabled(ctx, switchSupplierStatementFullPayment)
	if err != nil {
		return StatementGeneratorRulesResponse{}, err
	}
	return StatementGeneratorRulesResponse{
		CustomerStatementReceiptAmountZero: customer,
		SupplierStatementFullPayment:       supplier,
	}, nil
}

func (s GeneralSettingService) Detail(ctx context.Context, id int64) (NoRuleResponse, error) {
	if s.db == nil {
		return NoRuleResponse{}, errors.New("database client is not configured")
	}
	row, err := s.noRuleByID(ctx, id)
	if err != nil {
		return NoRuleResponse{}, err
	}
	return noRuleResponse(row), nil
}

func (s GeneralSettingService) NextNumber(ctx context.Context, moduleKey string) (NoRuleGenerateResponse, error) {
	if s.db == nil {
		return NoRuleGenerateResponse{}, errors.New("database client is not configured")
	}
	moduleKey = strings.TrimSpace(moduleKey)
	if moduleKey == "" {
		return NoRuleGenerateResponse{}, NewAuthError(AuthErrorValidation, "moduleKey不能为空")
	}
	if enabled, err := s.switchEnabled(ctx, switchUseSnowflakeBusinessNo); err != nil {
		return NoRuleGenerateResponse{}, err
	} else if enabled {
		generated := fmt.Sprintf("%d", s.idGenerator.Next())
		return NoRuleGenerateResponse{ModuleKey: moduleKey, GeneratedNo: generated, GeneratedID: &generated}, nil
	}
	ruleCode := moduleRuleCodeMap[moduleKey]
	if ruleCode == "" {
		return NoRuleGenerateResponse{ModuleKey: moduleKey}, nil
	}
	next, err := s.nextRuleValue(ctx, ruleCode)
	if err != nil {
		return NoRuleGenerateResponse{}, err
	}
	return NoRuleGenerateResponse{ModuleKey: moduleKey, GeneratedNo: next}, nil
}

func (s GeneralSettingService) Create(ctx context.Context, request NoRuleRequest) (NoRuleResponse, error) {
	if s.db == nil {
		return NoRuleResponse{}, errors.New("database client is not configured")
	}
	normalized, err := normalizeNoRuleRequest(request)
	if err != nil {
		return NoRuleResponse{}, err
	}
	if err := validateNoRuleTemplate(normalized); err != nil {
		return NoRuleResponse{}, err
	}
	id := s.idGenerator.Next()
	_, err = s.db.Exec(ctx, `
		INSERT INTO sys_no_rule (
			id, setting_code, setting_name, bill_name, prefix, date_rule,
			serial_length, reset_rule, sample_no, status, remark,
			created_by, created_name, deleted_flag
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, 0, 'system', false)
	`, id, normalized.SettingCode, normalized.SettingName, normalized.BillName, normalized.Prefix,
		normalized.DateRule, normalized.SerialLength, normalized.ResetRule, normalized.SampleNo,
		normalized.Status, optionalNullString(normalized.Remark))
	if err != nil {
		if isUniqueViolation(err) {
			return NoRuleResponse{}, NewAuthError(AuthErrorBusiness, "设置编码已存在")
		}
		return NoRuleResponse{}, err
	}
	return s.Detail(ctx, id)
}

func (s GeneralSettingService) Update(ctx context.Context, id int64, request NoRuleRequest) (NoRuleResponse, error) {
	if s.db == nil {
		return NoRuleResponse{}, errors.New("database client is not configured")
	}
	current, err := s.noRuleByID(ctx, id)
	if err != nil {
		return NoRuleResponse{}, err
	}
	normalized, err := normalizeNoRuleRequest(request)
	if err != nil {
		return NoRuleResponse{}, err
	}
	if current.SettingCode != normalized.SettingCode {
		if err := s.ensureNoRuleCodeAvailable(ctx, normalized.SettingCode); err != nil {
			return NoRuleResponse{}, err
		}
	}
	if err := validateNoRuleTemplate(normalized); err != nil {
		return NoRuleResponse{}, err
	}
	commandTag, err := s.db.Exec(ctx, `
		UPDATE sys_no_rule
		   SET setting_code = $2,
		       setting_name = $3,
		       bill_name = $4,
		       prefix = $5,
		       date_rule = $6,
		       serial_length = $7,
		       reset_rule = $8,
		       sample_no = $9,
		       status = $10,
		       remark = $11,
		       updated_by = 0,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id, normalized.SettingCode, normalized.SettingName, normalized.BillName, normalized.Prefix,
		normalized.DateRule, normalized.SerialLength, normalized.ResetRule, normalized.SampleNo,
		normalized.Status, optionalNullString(normalized.Remark))
	if err != nil {
		if isUniqueViolation(err) {
			return NoRuleResponse{}, NewAuthError(AuthErrorBusiness, "设置编码已存在")
		}
		return NoRuleResponse{}, err
	}
	if commandTag.RowsAffected() == 0 {
		return NoRuleResponse{}, NewAuthError(AuthErrorNotFound, "单号规则不存在")
	}
	return s.Detail(ctx, id)
}

func (s GeneralSettingService) Delete(ctx context.Context, id int64) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	if _, err := s.noRuleByID(ctx, id); err != nil {
		return err
	}
	commandTag, err := s.db.Exec(ctx, `
		UPDATE sys_no_rule
		   SET deleted_flag = true,
		       updated_by = 0,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id)
	if err != nil {
		return err
	}
	if commandTag.RowsAffected() == 0 {
		return NewAuthError(AuthErrorNotFound, "单号规则不存在")
	}
	return nil
}

func (s GeneralSettingService) generalSettingRows(ctx context.Context) ([]GeneralSettingResponse, error) {
	rules, err := s.noRuleRows(ctx)
	if err != nil {
		return nil, err
	}
	result := make([]GeneralSettingResponse, 0, len(rules))
	for _, rule := range rules {
		result = append(result, generalSettingFromNoRule(rule))
	}
	uploadRules, err := s.uploadRules.Summaries(ctx)
	if err != nil {
		return nil, err
	}
	result = append(result, uploadRules...)
	return result, nil
}

func (s GeneralSettingService) generalSettingsByCodes(ctx context.Context, codes map[string]struct{}) ([]GeneralSettingResponse, error) {
	if s.db == nil {
		return nil, errors.New("database client is not configured")
	}
	rules, err := s.noRuleRows(ctx)
	if err != nil {
		return nil, err
	}
	result := make([]GeneralSettingResponse, 0, len(codes))
	for _, rule := range rules {
		if _, ok := codes[rule.SettingCode]; ok {
			result = append(result, generalSettingFromNoRule(rule))
		}
	}
	sortGeneralSettings(result)
	return result, nil
}

func (s GeneralSettingService) noRuleRows(ctx context.Context) ([]noRuleRecord, error) {
	rows, err := s.db.Query(ctx, `
		SELECT id, setting_code, setting_name, bill_name, prefix, date_rule,
		       serial_length, reset_rule, sample_no, status, COALESCE(remark, ''),
		       current_period, next_serial_value
		  FROM sys_no_rule
		 WHERE deleted_flag = false
		 ORDER BY id DESC
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := []noRuleRecord{}
	for rows.Next() {
		row, err := scanNoRule(rows)
		if err != nil {
			return nil, err
		}
		result = append(result, row)
	}
	return result, rows.Err()
}

func (s GeneralSettingService) noRuleByID(ctx context.Context, id int64) (noRuleRecord, error) {
	var row noRuleRecord
	err := s.db.QueryRow(ctx, `
		SELECT id, setting_code, setting_name, bill_name, prefix, date_rule,
		       serial_length, reset_rule, sample_no, status, COALESCE(remark, ''),
		       current_period, next_serial_value
		  FROM sys_no_rule
		 WHERE id = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, id).Scan(
		&row.ID, &row.SettingCode, &row.SettingName, &row.BillName, &row.Prefix,
		&row.DateRule, &row.SerialLength, &row.ResetRule, &row.SampleNo, &row.Status,
		&row.Remark, &row.CurrentPeriod, &row.NextSerialValue,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return noRuleRecord{}, NewAuthError(AuthErrorNotFound, "单号规则不存在")
	}
	return row, err
}

func (s GeneralSettingService) ensureNoRuleCodeAvailable(ctx context.Context, settingCode string) error {
	var exists bool
	if err := s.db.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1 FROM sys_no_rule
			 WHERE setting_code = $1
			   AND deleted_flag = false
		)
	`, settingCode).Scan(&exists); err != nil {
		return err
	}
	if exists {
		return NewAuthError(AuthErrorBusiness, "设置编码已存在")
	}
	return nil
}

func (s GeneralSettingService) switchEnabled(ctx context.Context, settingCode string) (bool, error) {
	var status string
	err := s.db.QueryRow(ctx, `
		SELECT status
		  FROM sys_no_rule
		 WHERE setting_code = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, settingCode).Scan(&status)
	if errors.Is(err, pgx.ErrNoRows) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return status == noRuleStatusNormal, nil
}

func (s GeneralSettingService) nextRuleValue(ctx context.Context, settingCode string) (string, error) {
	tx, err := s.db.Begin(ctx)
	if err != nil {
		return "", err
	}
	defer tx.Rollback(ctx)

	var rule noRuleRecord
	err = tx.QueryRow(ctx, `
		SELECT id, setting_code, setting_name, bill_name, prefix, date_rule,
		       serial_length, reset_rule, sample_no, status, COALESCE(remark, ''),
		       current_period, next_serial_value
		  FROM sys_no_rule
		 WHERE setting_code = $1
		   AND deleted_flag = false
		 FOR UPDATE
	`, settingCode).Scan(
		&rule.ID, &rule.SettingCode, &rule.SettingName, &rule.BillName, &rule.Prefix,
		&rule.DateRule, &rule.SerialLength, &rule.ResetRule, &rule.SampleNo, &rule.Status,
		&rule.Remark, &rule.CurrentPeriod, &rule.NextSerialValue,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return "", NewAuthError(AuthErrorBusiness, "编号规则不存在: "+settingCode)
	}
	if err != nil {
		return "", err
	}
	if rule.Status != noRuleStatusNormal {
		return "", NewAuthError(AuthErrorBusiness, "编号规则未启用: "+settingCode)
	}
	now := time.Now()
	period := noRulePeriod(rule.ResetRule, now)
	if !rule.CurrentPeriod.Valid || rule.CurrentPeriod.String != period || !rule.NextSerialValue.Valid || rule.NextSerialValue.Int64 < 1 {
		rule.CurrentPeriod = sql.NullString{String: period, Valid: true}
		rule.NextSerialValue = sql.NullInt64{Int64: 1, Valid: true}
	}
	serial := rule.NextSerialValue.Int64
	value := buildNoRuleValue(rule, now, serial)
	if len(value) > 64 {
		return "", NewAuthError(AuthErrorValidation, "编号规则生成结果长度不能超过64")
	}
	if _, err := tx.Exec(ctx, `
		UPDATE sys_no_rule
		   SET current_period = $2,
		       next_serial_value = $3,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
	`, rule.ID, rule.CurrentPeriod.String, serial+1); err != nil {
		return "", err
	}
	if err := tx.Commit(ctx); err != nil {
		return "", err
	}
	return value, nil
}

type noRuleRecord struct {
	ID              int64
	SettingCode     string
	SettingName     string
	BillName        string
	Prefix          string
	DateRule        string
	SerialLength    int
	ResetRule       string
	SampleNo        string
	Status          string
	Remark          string
	CurrentPeriod   sql.NullString
	NextSerialValue sql.NullInt64
}

func scanNoRule(rows pgx.Rows) (noRuleRecord, error) {
	var row noRuleRecord
	err := rows.Scan(
		&row.ID, &row.SettingCode, &row.SettingName, &row.BillName, &row.Prefix,
		&row.DateRule, &row.SerialLength, &row.ResetRule, &row.SampleNo, &row.Status,
		&row.Remark, &row.CurrentPeriod, &row.NextSerialValue,
	)
	return row, err
}

func noRuleResponse(row noRuleRecord) NoRuleResponse {
	return NoRuleResponse{
		ID:           row.ID,
		SettingCode:  row.SettingCode,
		SettingName:  row.SettingName,
		BillName:     row.BillName,
		Prefix:       row.Prefix,
		DateRule:     row.DateRule,
		SerialLength: row.SerialLength,
		ResetRule:    row.ResetRule,
		SampleNo:     row.SampleNo,
		Status:       row.Status,
		Remark:       row.Remark,
	}
}

func generalSettingFromNoRule(row noRuleRecord) GeneralSettingResponse {
	return GeneralSettingResponse{
		ID:           row.ID,
		SettingCode:  row.SettingCode,
		SettingName:  row.SettingName,
		BillName:     row.BillName,
		Prefix:       row.Prefix,
		DateRule:     row.DateRule,
		SerialLength: row.SerialLength,
		ResetRule:    row.ResetRule,
		SampleNo:     row.SampleNo,
		Status:       row.Status,
		Remark:       row.Remark,
		RuleType:     noRuleTypeGeneral,
	}
}

func normalizeNoRuleRequest(request NoRuleRequest) (NoRuleRequest, error) {
	request.SettingCode = strings.TrimSpace(request.SettingCode)
	request.SettingName = strings.TrimSpace(request.SettingName)
	request.BillName = strings.TrimSpace(request.BillName)
	request.Prefix = strings.TrimSpace(request.Prefix)
	request.DateRule = strings.TrimSpace(request.DateRule)
	request.ResetRule = strings.TrimSpace(request.ResetRule)
	request.SampleNo = strings.TrimSpace(request.SampleNo)
	request.Status = strings.TrimSpace(request.Status)
	request.Remark = strings.TrimSpace(request.Remark)
	if request.Status == "" {
		request.Status = noRuleStatusNormal
	}
	if request.SettingCode == "" {
		return NoRuleRequest{}, NewAuthError(AuthErrorValidation, "设置编码不能为空")
	}
	if request.SettingName == "" {
		return NoRuleRequest{}, NewAuthError(AuthErrorValidation, "设置名称不能为空")
	}
	if request.BillName == "" {
		return NoRuleRequest{}, NewAuthError(AuthErrorValidation, "单据名称不能为空")
	}
	if request.Prefix == "" {
		return NoRuleRequest{}, NewAuthError(AuthErrorValidation, "前缀不能为空")
	}
	if request.DateRule == "" {
		return NoRuleRequest{}, NewAuthError(AuthErrorValidation, "日期规则不能为空")
	}
	if request.ResetRule == "" {
		return NoRuleRequest{}, NewAuthError(AuthErrorValidation, "重置规则不能为空")
	}
	if request.SampleNo == "" {
		return NoRuleRequest{}, NewAuthError(AuthErrorValidation, "示例编号不能为空")
	}
	if request.SerialLength == nil || *request.SerialLength < 1 {
		return NoRuleRequest{}, NewAuthError(AuthErrorValidation, "流水号长度必须大于等于1")
	}
	if request.Status != noRuleStatusNormal && request.Status != noRuleStatusDisabled {
		return NoRuleRequest{}, NewAuthError(AuthErrorValidation, "状态不合法")
	}
	if len(request.SettingCode) > 64 || len(request.SettingName) > 128 || len(request.BillName) > 128 ||
		len(request.Prefix) > 64 || len(request.DateRule) > 32 || len(request.ResetRule) > 32 ||
		len(request.SampleNo) > 64 || len(request.Status) > 16 || len(request.Remark) > 255 {
		return NoRuleRequest{}, NewAuthError(AuthErrorValidation, "字段长度超出限制")
	}
	return request, nil
}

func validateNoRuleTemplate(request NoRuleRequest) error {
	if request.SettingCode == switchDetailedOperationLogActions {
		return validateDetailedOperationLogActions(request.SampleNo)
	}
	if !strings.HasPrefix(request.SettingCode, "RULE_") || !noRuleUsesMagicVariables(request.Prefix) {
		return nil
	}
	if !noRuleContainsSequenceToken(request.Prefix) {
		return NewAuthError(AuthErrorBusiness, "单号规则模板必须包含 {seq} 变量")
	}
	return nil
}

func validateDetailedOperationLogActions(sampleNo string) error {
	selected := strings.Split(sampleNo, ",")
	hasAny := false
	for _, item := range selected {
		key := strings.ToUpper(strings.TrimSpace(item))
		if key == "" {
			continue
		}
		hasAny = true
		if _, ok := detailedOperationLogActions[key]; !ok {
			return NewAuthError(AuthErrorBusiness, "页面操作详细日志包含不支持的记录动作")
		}
	}
	if !hasAny {
		return NewAuthError(AuthErrorBusiness, "页面操作详细日志至少需要勾选一个记录动作")
	}
	return nil
}

func sortGeneralSettings(rows []GeneralSettingResponse) {
	sort.SliceStable(rows, func(i, j int) bool {
		leftOrder := generalSettingOrder[rows[i].SettingCode]
		if leftOrder == 0 {
			leftOrder = 500
		}
		rightOrder := generalSettingOrder[rows[j].SettingCode]
		if rightOrder == 0 {
			rightOrder = 500
		}
		if leftOrder != rightOrder {
			return leftOrder < rightOrder
		}
		if rows[i].RuleType != rows[j].RuleType {
			return rows[i].RuleType == noRuleTypeGeneral
		}
		if rows[i].BillName != rows[j].BillName {
			return rows[i].BillName < rows[j].BillName
		}
		return rows[i].SettingCode < rows[j].SettingCode
	})
}

func generalSettingMatchesKeyword(row GeneralSettingResponse, keyword string) bool {
	return containsLower(row.SettingCode, keyword) ||
		containsLower(row.SettingName, keyword) ||
		containsLower(row.BillName, keyword) ||
		containsLower(row.Prefix, keyword) ||
		containsLower(row.Remark, keyword)
}

func noRuleUsesMagicVariables(template string) bool {
	return strings.Contains(template, "{") && strings.Contains(template, "}")
}

func noRuleContainsSequenceToken(template string) bool {
	return strings.Contains(strings.ToLower(template), "{seq}")
}

func buildNoRuleValue(rule noRuleRecord, date time.Time, serial int64) string {
	template := strings.TrimSpace(rule.Prefix)
	if noRuleUsesMagicVariables(template) {
		return resolveNoRuleTemplate(template, rule, date, serial)
	}
	return noRuleDateSegment(rule.DateRule, date) + strings.ToUpper(template) + noRuleSerial(serial, rule.SerialLength)
}

func resolveNoRuleTemplate(template string, rule noRuleRecord, date time.Time, serial int64) string {
	replacements := map[string]string{
		"{date}":     noRuleDateSegment(rule.DateRule, date),
		"{yyyy}":     date.Format("2006"),
		"{yy}":       date.Format("06"),
		"{mm}":       date.Format("01"),
		"{dd}":       date.Format("02"),
		"{yyyymm}":   date.Format("200601"),
		"{yyyymmdd}": date.Format("20060102"),
		"{seq}":      noRuleSerial(serial, rule.SerialLength),
	}
	result := template
	for key, value := range replacements {
		result = strings.ReplaceAll(result, key, value)
		result = strings.ReplaceAll(result, strings.ToUpper(key), value)
	}
	return result
}

func noRuleSerial(serial int64, length int) string {
	if length < 1 {
		length = 1
	}
	return fmt.Sprintf("%0*d", length, serial)
}

func noRuleDateSegment(dateRule string, date time.Time) string {
	switch strings.ToUpper(strings.TrimSpace(dateRule)) {
	case "YYYYMM":
		return date.Format("200601")
	case "NONE":
		return ""
	default:
		return date.Format("2006")
	}
}

func noRulePeriod(resetRule string, date time.Time) string {
	switch strings.ToUpper(strings.TrimSpace(resetRule)) {
	case "MONTHLY":
		return date.Format("200601")
	case "NEVER":
		return "NEVER"
	default:
		return date.Format("2006")
	}
}

func uploadRuleModuleName(moduleKey string) string {
	moduleKey = strings.TrimSpace(moduleKey)
	if moduleKey == "" {
		return "附件上传"
	}
	return uploadRuleModuleLabel(moduleKey)
}
