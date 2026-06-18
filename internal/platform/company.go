package platform

import (
	"context"
	"encoding/json"
	"errors"
	"sort"
	"strconv"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

const defaultTaxRateSettingCode = "SYS_DEFAULT_TAX_RATE"

type CompanyService struct {
	db          *pgxpool.Pool
	idGenerator *IDGenerator
}

type CompanySetting struct {
	ID                 int64                      `json:"id"`
	CompanyName        string                     `json:"companyName"`
	TaxNo              string                     `json:"taxNo"`
	BankName           string                     `json:"bankName"`
	BankAccount        string                     `json:"bankAccount"`
	TaxRate            float64                    `json:"taxRate"`
	SettlementAccounts []CompanySettlementAccount `json:"settlementAccounts"`
	Status             string                     `json:"status"`
	Remark             string                     `json:"remark"`
}

type CompanySettlementAccount struct {
	ID          int64  `json:"id,omitempty"`
	AccountName string `json:"accountName"`
	BankName    string `json:"bankName"`
	BankAccount string `json:"bankAccount"`
	UsageType   string `json:"usageType"`
	Status      string `json:"status"`
	Remark      string `json:"remark"`
}

func NewCompanyService(db *pgxpool.Pool, machineID int64) CompanyService {
	return CompanyService{db: db, idGenerator: NewIDGenerator(machineID)}
}

func (s CompanyService) Page(ctx context.Context, query PageQuery, keyword string, status string) (PageResponse[CompanySetting], error) {
	if s.db == nil {
		return PageResponse[CompanySetting]{}, errors.New("database client is not configured")
	}
	rows, err := s.db.Query(ctx, `
		SELECT id, company_name, tax_no, bank_name, bank_account, tax_rate,
		       COALESCE(settlement_accounts_json::text, '[]'),
		       status, COALESCE(remark, '')
		  FROM sys_company_setting
		 WHERE deleted_flag = false
	`)
	if err != nil {
		return PageResponse[CompanySetting]{}, err
	}
	defer rows.Close()

	items := []CompanySetting{}
	for rows.Next() {
		var item CompanySetting
		var accountsJSON string
		if err := rows.Scan(&item.ID, &item.CompanyName, &item.TaxNo, &item.BankName, &item.BankAccount, &item.TaxRate, &accountsJSON, &item.Status, &item.Remark); err != nil {
			return PageResponse[CompanySetting]{}, err
		}
		item.SettlementAccounts = readCompanySettlementAccounts(accountsJSON, item)
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return PageResponse[CompanySetting]{}, err
	}
	filtered := make([]CompanySetting, 0, len(items))
	keyword = strings.ToLower(strings.TrimSpace(keyword))
	status = strings.TrimSpace(status)
	for _, item := range items {
		if keyword != "" && !containsLower(item.CompanyName, keyword) && !containsLower(item.TaxNo, keyword) && !containsLower(item.BankName, keyword) && !containsLower(item.BankAccount, keyword) {
			continue
		}
		if status != "" && item.Status != status {
			continue
		}
		filtered = append(filtered, item)
	}
	sort.Slice(filtered, func(i, j int) bool {
		if query.SortBy == "companyName" {
			if strings.EqualFold(query.Direction, "asc") {
				return filtered[i].CompanyName < filtered[j].CompanyName
			}
			return filtered[i].CompanyName > filtered[j].CompanyName
		}
		if strings.EqualFold(query.Direction, "asc") {
			return filtered[i].ID < filtered[j].ID
		}
		return filtered[i].ID > filtered[j].ID
	})
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

func (s CompanyService) Detail(ctx context.Context, id int64) (CompanySetting, error) {
	if s.db == nil {
		return CompanySetting{}, errors.New("database client is not configured")
	}
	var item CompanySetting
	var accountsJSON string
	err := s.db.QueryRow(ctx, `
		SELECT id, company_name, tax_no, bank_name, bank_account, tax_rate,
		       COALESCE(settlement_accounts_json::text, '[]'),
		       status, COALESCE(remark, '')
		  FROM sys_company_setting
		 WHERE id = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, id).Scan(&item.ID, &item.CompanyName, &item.TaxNo, &item.BankName, &item.BankAccount, &item.TaxRate, &accountsJSON, &item.Status, &item.Remark)
	if errors.Is(err, pgx.ErrNoRows) {
		return CompanySetting{}, NewAuthError(AuthErrorNotFound, "公司信息不存在")
	}
	if err != nil {
		return CompanySetting{}, err
	}
	item.SettlementAccounts = readCompanySettlementAccounts(accountsJSON, item)
	if taxRate, err := s.currentTaxRate(ctx); err == nil {
		item.TaxRate = taxRate
	}
	return item, nil
}

func (s CompanyService) Current(ctx context.Context) (*CompanySetting, error) {
	if s.db == nil {
		return nil, errors.New("database client is not configured")
	}
	current, err := s.loadCurrent(ctx)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &current, nil
}

func (s CompanyService) Create(ctx context.Context, request CompanySetting) (CompanySetting, error) {
	if s.db == nil {
		return CompanySetting{}, errors.New("database client is not configured")
	}
	return CompanySetting{}, NewAuthError(AuthErrorBusiness, "公司信息仅允许通过首次初始化页面创建")
}

func (s CompanyService) SaveCurrent(ctx context.Context, request CompanySetting) (CompanySetting, error) {
	if s.db == nil {
		return CompanySetting{}, errors.New("database client is not configured")
	}
	tx, err := s.db.Begin(ctx)
	if err != nil {
		return CompanySetting{}, err
	}
	defer tx.Rollback(ctx)

	current, err := s.loadCurrentWithQuerier(ctx, tx)
	if errors.Is(err, pgx.ErrNoRows) {
		return CompanySetting{}, NewAuthError(AuthErrorBusiness, "请先通过首次初始化页面创建公司信息")
	}
	if err != nil {
		return CompanySetting{}, err
	}
	if current.CompanyName != "" && current.CompanyName != strings.TrimSpace(request.CompanyName) {
		return CompanySetting{}, NewAuthError(AuthErrorBusiness, "公司名称由首次初始化写入，不允许修改")
	}
	if current.TaxNo != "" && current.TaxNo != strings.TrimSpace(request.TaxNo) {
		return CompanySetting{}, NewAuthError(AuthErrorBusiness, "税号由首次初始化写入，不允许修改")
	}
	accounts, err := s.normalizeSettlementAccounts(request.SettlementAccounts)
	if err != nil {
		return CompanySetting{}, err
	}
	primary := accounts[0]
	taxRate := request.TaxRate
	if taxRate == 0 {
		taxRate = current.TaxRate
	}
	status := strings.TrimSpace(request.Status)
	if status == "" {
		status = "正常"
	}
	accountsJSON, err := json.Marshal(accounts)
	if err != nil {
		return CompanySetting{}, err
	}
	_, err = tx.Exec(ctx, `
		UPDATE sys_company_setting
		   SET company_name = $1,
		       tax_no = $2,
		       bank_name = $3,
		       bank_account = $4,
		       tax_rate = $5,
		       settlement_accounts_json = $6::jsonb,
		       status = $7,
		       remark = $8,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $9
		   AND deleted_flag = false
	`, strings.TrimSpace(request.CompanyName), strings.TrimSpace(request.TaxNo), primary.BankName, primary.BankAccount, taxRate, string(accountsJSON), status, strings.TrimSpace(request.Remark), current.ID)
	if err != nil {
		return CompanySetting{}, err
	}
	if err := s.upsertTaxRateWithQuerier(ctx, tx, taxRate); err != nil {
		return CompanySetting{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return CompanySetting{}, err
	}
	return s.loadCurrent(ctx)
}

func (s CompanyService) Update(ctx context.Context, id int64, request CompanySetting) (CompanySetting, error) {
	if s.db == nil {
		return CompanySetting{}, errors.New("database client is not configured")
	}
	current, err := s.Detail(ctx, id)
	if err != nil {
		return CompanySetting{}, err
	}
	if request.CompanyName == "" {
		request.CompanyName = current.CompanyName
	}
	if request.TaxNo == "" {
		request.TaxNo = current.TaxNo
	}
	return s.SaveCurrent(ctx, request)
}

func (s CompanyService) Delete(ctx context.Context, id int64) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	command, err := s.db.Exec(ctx, `
		UPDATE sys_company_setting
		   SET deleted_flag = true,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id)
	if err != nil {
		return err
	}
	if command.RowsAffected() == 0 {
		return NewAuthError(AuthErrorNotFound, "公司信息不存在")
	}
	return nil
}

func (s CompanyService) loadCurrent(ctx context.Context) (CompanySetting, error) {
	return s.loadCurrentWithQuerier(ctx, s.db)
}

type companyQuerier interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
}

func (s CompanyService) loadCurrentWithQuerier(ctx context.Context, querier companyQuerier) (CompanySetting, error) {
	var current CompanySetting
	var accountsJSON string
	err := querier.QueryRow(ctx, `
		SELECT id, company_name, tax_no, bank_name, bank_account, tax_rate,
		       COALESCE(settlement_accounts_json::text, '[]'),
		       status, COALESCE(remark, '')
		  FROM sys_company_setting
		 WHERE deleted_flag = false
		 ORDER BY CASE WHEN status = '正常' THEN 0 ELSE 1 END, id ASC
		 LIMIT 1
	`).Scan(&current.ID, &current.CompanyName, &current.TaxNo, &current.BankName, &current.BankAccount, &current.TaxRate, &accountsJSON, &current.Status, &current.Remark)
	if err != nil {
		return CompanySetting{}, err
	}
	current.SettlementAccounts = readCompanySettlementAccounts(accountsJSON, current)
	if taxRate, err := s.currentTaxRateWithQuerier(ctx, querier); err == nil {
		current.TaxRate = taxRate
	}
	return current, nil
}

func readCompanySettlementAccounts(raw string, fallback CompanySetting) []CompanySettlementAccount {
	var accounts []CompanySettlementAccount
	if strings.TrimSpace(raw) != "" {
		_ = json.Unmarshal([]byte(raw), &accounts)
	}
	if len(accounts) > 0 {
		return accounts
	}
	if fallback.BankName == "" || fallback.BankAccount == "" {
		return []CompanySettlementAccount{}
	}
	return []CompanySettlementAccount{{
		ID:          fallback.ID,
		AccountName: fallback.CompanyName,
		BankName:    fallback.BankName,
		BankAccount: fallback.BankAccount,
		UsageType:   "通用",
		Status:      fallback.Status,
		Remark:      fallback.Remark,
	}}
}

func (s CompanyService) normalizeSettlementAccounts(accounts []CompanySettlementAccount) ([]CompanySettlementAccount, error) {
	if len(accounts) == 0 {
		return nil, NewAuthError(AuthErrorValidation, "至少需要维护一个结算账户")
	}
	used := map[string]struct{}{}
	result := make([]CompanySettlementAccount, 0, len(accounts))
	for i, account := range accounts {
		account.AccountName = strings.TrimSpace(account.AccountName)
		account.BankName = strings.TrimSpace(account.BankName)
		account.BankAccount = strings.TrimSpace(account.BankAccount)
		account.UsageType = strings.TrimSpace(account.UsageType)
		account.Status = strings.TrimSpace(account.Status)
		account.Remark = strings.TrimSpace(account.Remark)
		if account.AccountName == "" {
			return nil, NewAuthError(AuthErrorValidation, "第"+strconv.Itoa(i+1)+"行账户名称不能为空")
		}
		if account.BankName == "" {
			return nil, NewAuthError(AuthErrorValidation, "第"+strconv.Itoa(i+1)+"行开户银行不能为空")
		}
		if account.BankAccount == "" {
			return nil, NewAuthError(AuthErrorValidation, "第"+strconv.Itoa(i+1)+"行银行账号不能为空")
		}
		if account.UsageType == "" {
			return nil, NewAuthError(AuthErrorValidation, "第"+strconv.Itoa(i+1)+"行用途不能为空")
		}
		if account.Status == "" {
			return nil, NewAuthError(AuthErrorValidation, "第"+strconv.Itoa(i+1)+"行状态不能为空")
		}
		if _, ok := used[account.BankAccount]; ok {
			return nil, NewAuthError(AuthErrorValidation, "银行账号不能重复: "+account.BankAccount)
		}
		used[account.BankAccount] = struct{}{}
		if account.ID == 0 {
			account.ID = s.idGenerator.Next()
		}
		result = append(result, account)
	}
	return result, nil
}

func (s CompanyService) currentTaxRate(ctx context.Context) (float64, error) {
	return s.currentTaxRateWithQuerier(ctx, s.db)
}

func (s CompanyService) currentTaxRateWithQuerier(ctx context.Context, querier companyQuerier) (float64, error) {
	var sample string
	err := querier.QueryRow(ctx, `
		SELECT sample_no
		  FROM sys_no_rule
		 WHERE setting_code = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, defaultTaxRateSettingCode).Scan(&sample)
	if err != nil {
		return 0, err
	}
	return parseFloatOrZero(sample), nil
}

func (s CompanyService) upsertTaxRate(ctx context.Context, taxRate float64) error {
	return s.upsertTaxRateWithQuerier(ctx, s.db, taxRate)
}

func (s CompanyService) upsertTaxRateWithQuerier(ctx context.Context, querier companyQuerier, taxRate float64) error {
	command, err := querier.Exec(ctx, `
		UPDATE sys_no_rule
		   SET sample_no = to_char($1::numeric, 'FM999999990.0000'),
		       updated_at = CURRENT_TIMESTAMP
		 WHERE setting_code = $2
		   AND deleted_flag = false
	`, taxRate, defaultTaxRateSettingCode)
	if err != nil {
		return err
	}
	if command.RowsAffected() > 0 {
		return nil
	}
	_, err = querier.Exec(ctx, `
		INSERT INTO sys_no_rule (
			id, setting_code, setting_name, bill_name, prefix, date_rule,
			serial_length, reset_rule, sample_no, status, remark,
			created_by, created_name, deleted_flag
		) VALUES ($1, $2, '默认税率', '发票税率', 'SYS', 'yyyy',
			1, 'YEARLY', to_char($3::numeric, 'FM999999990.0000'), '正常',
			'用于发票默认税率与税额自动计算', 0, 'system', false)
	`, s.idGenerator.Next(), defaultTaxRateSettingCode, taxRate)
	return err
}
